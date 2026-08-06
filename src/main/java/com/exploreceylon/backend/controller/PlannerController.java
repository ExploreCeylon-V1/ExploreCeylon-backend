package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.service.planner.PlannerFacadeService;
import com.exploreceylon.backend.service.planner.PlannerPersistenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Production-ready REST Controller for End-to-End Trip Planning & Persistence API.
 * Exposes endpoints for planner generation, persistent saving, listing, retrieval, and soft deletion.
 */
@RestController
@RequestMapping("/api/v1/planner")
@RequiredArgsConstructor
@Slf4j
public class PlannerController {

    private final PlannerFacadeService plannerFacadeService;
    private final PlannerPersistenceService plannerPersistenceService;
    private final UserRepository userRepository;

    @PostMapping("/generate")
    public ResponseEntity<PlannerResponse> generatePlannerItinerary(@Valid @RequestBody PlannerRequest request) {
        log.info("Received End-to-End Trip Planning Request: {} -> {} ({} days)",
                request.getOrigin(), request.getDestination(), request.getTripDays());

        PlannerResponse response = plannerFacadeService.generateItinerary(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-and-save")
    public ResponseEntity<PlannerSaveResponse> generateAndSaveItinerary(@Valid @RequestBody PlannerSaveRequest request) {
        User user = getCurrentUser();
        log.info("Received Generate & Save Request from User: {} ({} -> {})",
                user.getEmail(), request.getPlannerRequest().getOrigin(), request.getPlannerRequest().getDestination());

        PlannerSaveResponse response = plannerPersistenceService.generateAndSave(request, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trips")
    public ResponseEntity<List<PlannerTripSummary>> getMyGeneratedTrips() {
        User user = getCurrentUser();
        List<PlannerTripSummary> trips = plannerPersistenceService.getUserGeneratedTrips(user);
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<PlannerResponse> getGeneratedTripById(@PathVariable Long tripId) {
        User user = getCurrentUser();
        PlannerResponse response = plannerPersistenceService.getGeneratedTripById(tripId, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/trips/{tripId}")
    public ResponseEntity<Void> softDeleteTrip(@PathVariable Long tripId) {
        User user = getCurrentUser();
        plannerPersistenceService.softDeleteTrip(tripId, user);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}
