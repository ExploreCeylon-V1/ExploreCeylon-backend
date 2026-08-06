package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.service.planner.PlannerFacadeService;
import com.exploreceylon.backend.service.planner.PlannerPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PlannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlannerFacadeService plannerFacadeService;

    @MockBean
    private PlannerPersistenceService plannerPersistenceService;

    @MockBean
    private UserRepository userRepository;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).email("user@example.com").role(User.Role.TRAVELER).build();
        Mockito.when(userRepository.findByEmail(any())).thenReturn(Optional.of(mockUser));
    }

    @Test
    @DisplayName("POST /api/v1/planner/generate should return HTTP 200 with PlannerResponse DTO")
    void testGeneratePlannerEndpointSuccess() throws Exception {
        PlannerResponse mockResponse = PlannerResponse.builder()
                .summary(PlannerSummary.builder()
                        .origin("Colombo")
                        .destination("Kandy")
                        .tripDays(2)
                        .travelStyle("RELAXED")
                        .budget("MID_RANGE")
                        .groupSize(2)
                        .overallScore(95.0)
                        .build())
                .days(List.of())
                .qualityScore(95.0)
                .build();

        Mockito.when(plannerFacadeService.generateItinerary(any())).thenReturn(mockResponse);

        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(2)
                .budget("MID_RANGE")
                .travelStyle("RELAXED")
                .groupSize(2)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        mockMvc.perform(post("/api/v1/planner/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.origin").value("Colombo"))
                .andExpect(jsonPath("$.summary.destination").value("Kandy"))
                .andExpect(jsonPath("$.qualityScore").value(95.0));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("POST /api/v1/planner/generate-and-save should return HTTP 200 with PlannerSaveResponse DTO")
    void testGenerateAndSaveEndpointSuccess() throws Exception {
        PlannerSaveResponse mockSaveResponse = PlannerSaveResponse.builder()
                .tripId(500L)
                .shareToken("share-500")
                .status(TripStatus.GENERATED)
                .createdAt(LocalDateTime.now())
                .plannerResponse(PlannerResponse.builder().build())
                .build();

        Mockito.when(plannerPersistenceService.generateAndSave(any(), any())).thenReturn(mockSaveResponse);

        PlannerSaveRequest saveRequest = PlannerSaveRequest.builder()
                .plannerRequest(PlannerRequest.builder().origin("Colombo").destination("Kandy").tripDays(2).groupSize(2).build())
                .build();

        mockMvc.perform(post("/api/v1/planner/generate-and-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(500L))
                .andExpect(jsonPath("$.shareToken").value("share-500"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("GET /api/v1/planner/trips should return user's generated trip summaries")
    void testGetMyGeneratedTrips() throws Exception {
        PlannerTripSummary summary = PlannerTripSummary.builder()
                .tripId(10L)
                .title("Colombo to Kandy Trip")
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .status(TripStatus.GENERATED)
                .build();

        Mockito.when(plannerPersistenceService.getUserGeneratedTrips(any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/planner/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripId").value(10L))
                .andExpect(jsonPath("$[0].fromLocation").value("Colombo"));
    }
}
