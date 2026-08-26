package com.exploreceylon.backend;

import com.exploreceylon.backend.dto.verification.RejectVerificationRequest;
import com.exploreceylon.backend.model.TourGuide;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.User.KycStatus;
import com.exploreceylon.backend.model.UserVerification;
import com.exploreceylon.backend.model.UserVerification.DocumentType;
import com.exploreceylon.backend.model.UserVerification.VerificationStatus;
import com.exploreceylon.backend.model.Vehicle;
import com.exploreceylon.backend.repository.GuideBookingRepository;
import com.exploreceylon.backend.repository.TourGuideRepository;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.repository.UserVerificationRepository;
import com.exploreceylon.backend.repository.VehicleBookingRepository;
import com.exploreceylon.backend.repository.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.exploreceylon.backend.service.EmailSenderService;
import com.exploreceylon.backend.service.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KycVerificationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserVerificationRepository verifications;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleBookingRepository vehicleBookings;
    @Autowired TourGuideRepository guides;
    @Autowired GuideBookingRepository guideBookings;

    @MockBean S3Service s3Service;
    @MockBean EmailSenderService emailSenderService;

    private final ObjectMapper mapper = new ObjectMapper();

    private Long vehicleId;
    private Long guideId;

    @BeforeEach
    void setUp() {
        Mockito.when(s3Service.uploadKycDocument(any(), any(), any(), any()))
                .thenReturn("kyc-documents/1/test-key.jpg");
        Mockito.when(s3Service.generatePresignedGetUrl(any(), any(Duration.class)))
                .thenReturn("https://test-bucket.s3.amazonaws.com/kyc-documents/sample.jpg?signed=true");

        vehicleBookings.deleteAll();
        guideBookings.deleteAll();
        verifications.deleteAll();
        vehicles.deleteAll();
        guides.deleteAll();
        users.deleteAll();

        Vehicle vehicle = new Vehicle();
        vehicle.setName("Test Hybrid Car");
        vehicle.setType(Vehicle.VehicleType.CAR);
        vehicle.setPricePerDay(40.0);
        vehicle.setDistrict("Galle");
        vehicle.setAvailable(true);
        vehicleId = vehicles.save(vehicle).getId();

        TourGuide guide = TourGuide.builder()
                .fullName("Sunil Perera")
                .languages("English,Sinhala")
                .specialties("CULTURAL")
                .district("Kandy")
                .pricePerDay(30.0)
                .available(true)
                .build();
        guideId = guides.save(guide).getId();
    }

    @AfterEach
    void tearDown() {
        vehicleBookings.deleteAll();
        guideBookings.deleteAll();
        verifications.deleteAll();
        vehicles.deleteAll();
        guides.deleteAll();
        users.deleteAll();
    }

    private String registerAndLogin(String email, String name) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Pass12345\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Pass12345\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(loginResult.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private String createAdminAndLogin(String email) throws Exception {
        String token = registerAndLogin(email, "Admin User");
        User admin = users.findByEmail(email).orElseThrow();
        admin.setRole(User.Role.ADMIN);
        users.save(admin);
        return token;
    }

    private MockMultipartFile dummyImage(String name, String originalName) {
        return new MockMultipartFile(
                name,
                originalName,
                "image/jpeg",
                "dummy image content".getBytes()
        );
    }

    @Test
    void submitSriLankanNic_withFrontAndBack_succeeds() throws Exception {
        String token = registerAndLogin("srilankan@example.com", "Nimal Perera");

        MockMultipartFile front = dummyImage("frontImage", "nic_front.jpg");
        MockMultipartFile back = dummyImage("backImage", "nic_back.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .file(back)
                        .param("nationality", "Sri Lankan")
                        .param("documentType", "NIC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.nationality").value("Sri Lankan"))
                .andExpect(jsonPath("$.documentType").value("NIC"));

        User user = users.findByEmail("srilankan@example.com").orElseThrow();
        assertEquals(KycStatus.PENDING, user.getKycStatus());
        assertEquals(1, verifications.count());
    }

    @Test
    void submitSriLankanNic_missingBackImage_isBadRequest() throws Exception {
        String token = registerAndLogin("missingback@example.com", "Kamal Silva");

        MockMultipartFile front = dummyImage("frontImage", "nic_front.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "Sri Lankan")
                        .param("documentType", "NIC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitSriLankan_withPassport_isBadRequest() throws Exception {
        String token = registerAndLogin("slpassport@example.com", "Anura Kumara");

        MockMultipartFile front = dummyImage("frontImage", "passport.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "Sri Lankan")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitForeigner_withPassportFrontOnly_succeeds() throws Exception {
        String token = registerAndLogin("tourist@example.com", "John Doe");

        MockMultipartFile front = dummyImage("frontImage", "passport.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "United States")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documentType").value("PASSPORT"));

        User user = users.findByEmail("tourist@example.com").orElseThrow();
        assertEquals(KycStatus.PENDING, user.getKycStatus());
    }

    @Test
    void submitForeigner_withNic_isBadRequest() throws Exception {
        String token = registerAndLogin("foreignnic@example.com", "Hans Mueller");

        MockMultipartFile front = dummyImage("frontImage", "nic_front.jpg");
        MockMultipartFile back = dummyImage("backImage", "nic_back.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .file(back)
                        .param("nationality", "Germany")
                        .param("documentType", "NIC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitVerification_whenAlreadyApproved_isBadRequest() throws Exception {
        String token = registerAndLogin("approveduser@example.com", "Approved User");
        User user = users.findByEmail("approveduser@example.com").orElseThrow();
        user.setKycStatus(KycStatus.APPROVED);
        users.save(user);

        MockMultipartFile front = dummyImage("frontImage", "passport.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "United Kingdom")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Identity is already verified. Re-submission is not permitted."));
    }

    @Test
    void submitVerification_whenAlreadyPending_isBadRequest() throws Exception {
        String token = registerAndLogin("pendinguser@example.com", "Pending User");

        MockMultipartFile front = dummyImage("frontImage", "passport.jpg");

        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "France")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Try submitting again while PENDING
        mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "France")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminApproveAndReject_flow() throws Exception {
        String adminToken = createAdminAndLogin("admin_approver@example.com");
        String userToken = registerAndLogin("applicant@example.com", "Applicant User");

        // 1. Submit
        MockMultipartFile front = dummyImage("frontImage", "passport.jpg");
        MvcResult submitResult = mvc.perform(multipart("/api/v1/verification/submit")
                        .file(front)
                        .param("nationality", "Australia")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode submitJson = mapper.readTree(submitResult.getResponse().getContentAsString());
        String verificationId = submitJson.get("verificationId").asText();

        // 2. Admin queries list (default / no filter)
        mvc.perform(get("/api/v1/admin/verification")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userName").value("Applicant User"));

        // 2b. Admin queries with status=ALL
        mvc.perform(get("/api/v1/admin/verification")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        // 2c. Admin queries with search
        mvc.perform(get("/api/v1/admin/verification")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Australia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        // 2d. Admin queries with status=PENDING
        mvc.perform(get("/api/v1/admin/verification")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userName").value("Applicant User"));

        // 3. Admin gets signed image URL
        mvc.perform(get("/api/v1/admin/verification/" + verificationId + "/image/front")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isNotEmpty());

        // 4. Admin rejects with empty reason -> 400
        mvc.perform(post("/api/v1/admin/verification/" + verificationId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        // 5. Admin rejects with valid reason
        mvc.perform(post("/api/v1/admin/verification/" + verificationId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Photo is blurred and unreadable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Photo is blurred and unreadable"));

        User userAfterReject = users.findByEmail("applicant@example.com").orElseThrow();
        assertEquals(KycStatus.REJECTED, userAfterReject.getKycStatus());

        // 6. User re-submits after rejection -> allowed!
        MockMultipartFile newFront = dummyImage("frontImage", "clear_passport.jpg");
        MvcResult resubmitResult = mvc.perform(multipart("/api/v1/verification/submit")
                        .file(newFront)
                        .param("nationality", "Australia")
                        .param("documentType", "PASSPORT")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String newVerificationId = mapper.readTree(resubmitResult.getResponse().getContentAsString())
                .get("verificationId").asText();

        // 7. Admin approves
        mvc.perform(post("/api/v1/admin/verification/" + newVerificationId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        User userAfterApprove = users.findByEmail("applicant@example.com").orElseThrow();
        assertEquals(KycStatus.APPROVED, userAfterApprove.getKycStatus());
    }

    @Test
    void bookingGateEnforcement_forVehicleAndGuide() throws Exception {
        String token = registerAndLogin("gatetraveler@example.com", "Gate Traveler");

        String vehicleBookingJson = "{\"vehicleId\":" + vehicleId + ","
                + "\"pickupDate\":\"" + LocalDate.now().plusDays(2) + "\","
                + "\"dropoffDate\":\"" + LocalDate.now().plusDays(4) + "\","
                + "\"pickupLocation\":\"Colombo Airport\"}";

        String guideBookingJson = "{\"guideId\":" + guideId + ","
                + "\"startDate\":\"" + LocalDate.now().plusDays(2) + "\","
                + "\"endDate\":\"" + LocalDate.now().plusDays(4) + "\"}";

        // 1. NOT_SUBMITTED -> 403 with code VERIFICATION_REQUIRED
        mvc.perform(post("/api/v1/vehicle-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(vehicleBookingJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFICATION_REQUIRED"));

        mvc.perform(post("/api/v1/guide-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(guideBookingJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFICATION_REQUIRED"));

        // 2. PENDING -> 403 with code VERIFICATION_PENDING
        User user = users.findByEmail("gatetraveler@example.com").orElseThrow();
        user.setKycStatus(KycStatus.PENDING);
        users.save(user);

        mvc.perform(post("/api/v1/vehicle-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(vehicleBookingJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFICATION_PENDING"));

        // 3. REJECTED -> 403 with code VERIFICATION_REJECTED
        user.setKycStatus(KycStatus.REJECTED);
        users.save(user);

        mvc.perform(post("/api/v1/vehicle-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(vehicleBookingJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFICATION_REJECTED"));

        // 4. APPROVED -> 200 OK for both vehicle and guide
        user.setKycStatus(KycStatus.APPROVED);
        users.save(user);

        mvc.perform(post("/api/v1/vehicle-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(vehicleBookingJson))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/guide-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(guideBookingJson))
                .andExpect(status().isOk());

        assertEquals(1, vehicleBookings.count());
        assertEquals(1, guideBookings.count());
    }
}
