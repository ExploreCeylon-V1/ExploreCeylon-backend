package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.planner.PlannerRequest;
import com.exploreceylon.backend.dto.planner.PlannerResponse;
import com.exploreceylon.backend.service.planner.PlannerFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Production-ready REST Controller for End-to-End Trip Planning API.
 * Delegates all planning pipeline execution directly to PlannerFacadeService.
 */
@RestController
@RequestMapping("/api/v1/planner")
@RequiredArgsConstructor
@Slf4j
public class PlannerController {

    private final PlannerFacadeService plannerFacadeService;

    @PostMapping("/generate")
    public ResponseEntity<PlannerResponse> generatePlannerItinerary(@Valid @RequestBody PlannerRequest request) {
        log.info("Received End-to-End Trip Planning Request: {} -> {} ({} days)",
                request.getOrigin(), request.getDestination(), request.getTripDays());

        PlannerResponse response = plannerFacadeService.generateItinerary(request);
        return ResponseEntity.ok(response);
    }
}
