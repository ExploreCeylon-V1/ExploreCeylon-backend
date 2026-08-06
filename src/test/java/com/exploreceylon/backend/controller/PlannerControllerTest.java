package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.planner.PlannerRequest;
import com.exploreceylon.backend.dto.planner.PlannerResponse;
import com.exploreceylon.backend.dto.planner.PlannerSummary;
import com.exploreceylon.backend.service.planner.PlannerFacadeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @DisplayName("POST /api/v1/planner/generate should return HTTP 400 Bad Request when origin is missing")
    void testGeneratePlannerEndpointValidationError() throws Exception {
        PlannerRequest invalidRequest = PlannerRequest.builder()
                .origin("") // Empty origin!
                .destination("Kandy")
                .tripDays(2)
                .build();

        mockMvc.perform(post("/api/v1/planner/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
