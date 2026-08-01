package com.exploreceylon.backend;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.Vehicle;
import com.exploreceylon.backend.repository.UserRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for vehicle booking creation and the admin-only
 * status update endpoint (owner/non-admin should be rejected, admin allowed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleBookingControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleBookingRepository bookings;

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String BOOKINGS = "/api/v1/vehicle-bookings";

    private final ObjectMapper mapper = new ObjectMapper();

    private Long vehicleId;

    @BeforeEach
    void setUp() throws Exception {
        bookings.deleteAll();
        vehicles.deleteAll();
        users.deleteAll();

        Vehicle vehicle = new Vehicle();
        vehicle.setName("Test Van");
        vehicle.setType(Vehicle.VehicleType.VAN);
        vehicle.setPricePerDay(50.0);
        vehicle.setDistrict("Colombo");
        vehicle.setAvailable(true);
        vehicleId = vehicles.save(vehicle).getId();
    }

    @AfterEach
    void tearDown() {
        // The H2 instance is shared/cached across test classes in this suite run
        // (DB_CLOSE_ON_EXIT=FALSE) -- clean up after the last test here too, or
        // leftover vehicle_bookings rows FK-block unrelated classes' users.deleteAll().
        bookings.deleteAll();
        vehicles.deleteAll();
        users.deleteAll();
    }

    private String registerAndLogin(String email) throws Exception {
        mvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"StrongPass1\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mvc.perform(post(LOGIN).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(loginResult.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private String bookVehicleJson() {
        return "{\"vehicleId\":" + vehicleId + ","
                + "\"pickupDate\":\"" + LocalDate.now().plusDays(1) + "\","
                + "\"dropoffDate\":\"" + LocalDate.now().plusDays(3) + "\","
                + "\"pickupLocation\":\"Colombo Airport\"}";
    }

    @Test
    void bookVehicle_asAuthenticatedUser_succeeds() throws Exception {
        String token = registerAndLogin("traveler1@example.com");

        mvc.perform(post(BOOKINGS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(bookVehicleJson()))
                .andExpect(status().isOk());

        assertEquals(1, bookings.count());
    }

    @Test
    void updateStatus_asNonAdminOwner_isForbidden() throws Exception {
        String token = registerAndLogin("traveler2@example.com");

        MvcResult bookingResult = mvc.perform(post(BOOKINGS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(bookVehicleJson()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(bookingResult.getResponse().getContentAsString());
        long bookingId = json.get("id").asLong();

        mvc.perform(patch(BOOKINGS + "/" + bookingId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "CONFIRMED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_asAdmin_succeeds() throws Exception {
        String ownerToken = registerAndLogin("traveler3@example.com");

        MvcResult bookingResult = mvc.perform(post(BOOKINGS)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(bookVehicleJson()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(bookingResult.getResponse().getContentAsString());
        long bookingId = json.get("id").asLong();

        // Register a second user, then promote to ADMIN directly via the repository.
        String adminToken = registerAndLogin("admin1@example.com");
        User admin = users.findByEmail("admin1@example.com").orElseThrow();
        admin.setRole(User.Role.ADMIN);
        users.save(admin);
        // Re-login so the token reflects... actually role isn't embedded in the JWT
        // (only the subject/email is), and JwtAuthFilter re-loads authorities from
        // the DB on every request, so the existing token already carries the new role.

        mvc.perform(patch(BOOKINGS + "/" + bookingId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk());
    }
}
