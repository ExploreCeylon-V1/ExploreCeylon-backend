package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.exception.UnauthenticatedException;
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
 * Exposes endpoints for planner generation, persistent saving, lifecycle management (confirm, duplicate, soft-delete).
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

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PlannerTripSummary> confirmTrip(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        log.info("Received Confirm Request for Trip ID {} from User {}", tripId, user.getEmail());
        PlannerTripSummary summary = plannerPersistenceService.confirmTrip(tripId, user);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<PlannerTripSummary> duplicateTrip(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        log.info("Received Duplicate Request for Trip ID {} from User {}", tripId, user.getEmail());
        PlannerTripSummary summary = plannerPersistenceService.duplicateTrip(tripId, user);
        return ResponseEntity.ok(summary);
    }

    @GetMapping({"/my-trips", "/trips"})
    public ResponseEntity<List<PlannerTripSummary>> getMyGeneratedTrips() {
        User user = getCurrentUser();
        List<PlannerTripSummary> trips = plannerPersistenceService.getUserGeneratedTrips(user);
        return ResponseEntity.ok(trips);
    }

    @GetMapping({"/{id}", "/trips/{id}"})
    public ResponseEntity<PlannerResponse> getGeneratedTripById(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        PlannerResponse response = plannerPersistenceService.getGeneratedTripById(tripId, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping({"/{id}", "/trips/{id}"})
    public ResponseEntity<Void> softDeleteTrip(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        plannerPersistenceService.softDeleteTrip(tripId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/{id}/restore", "/trips/{id}/restore"})
    public ResponseEntity<PlannerTripSummary> restoreTrip(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        PlannerTripSummary summary = plannerPersistenceService.restoreTrip(tripId, user);
        return ResponseEntity.ok(summary);
    }

    @GetMapping({"/{id}/activity-logs", "/trips/{id}/activity-logs"})
    public ResponseEntity<List<com.exploreceylon.backend.model.TripActivityLog>> getTripActivityLogs(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        List<com.exploreceylon.backend.model.TripActivityLog> logs = plannerPersistenceService.getTripActivityLogs(tripId, user);
        return ResponseEntity.ok(logs);
    }

    @PostMapping({"/{id}/share/revoke", "/trips/{id}/share/revoke"})
    public ResponseEntity<PlannerTripSummary> revokeShareToken(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        PlannerTripSummary summary = plannerPersistenceService.revokeShareToken(tripId, user);
        return ResponseEntity.ok(summary);
    }

    @PostMapping({"/{id}/share/regenerate", "/trips/{id}/share/regenerate"})
    public ResponseEntity<PlannerTripSummary> regenerateShareToken(@PathVariable("id") Long tripId) {
        User user = getCurrentUser();
        PlannerTripSummary summary = plannerPersistenceService.regenerateShareToken(tripId, user);
        return ResponseEntity.ok(summary);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthenticatedException("User not found: " + email));
    }
}
