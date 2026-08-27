package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.trip.*;
import com.exploreceylon.backend.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Slf4j
public class TripController {

    private final TripService tripService;
    private final com.exploreceylon.backend.service.BudgetService budgetService;

    // POST /api/v1/trips — Create trip
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.ok(tripService.createTrip(request));
    }

    // GET /api/v1/trips/my — Get my trips
    @GetMapping("/my")
    public ResponseEntity<List<TripResponse>> getMyTrips() {
        return ResponseEntity.ok(tripService.getMyTrips());
    }

    // GET /api/v1/trips/{id}
    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTripById(
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    // GET /api/v1/trips/share/{token} — Public share link
    @GetMapping("/share/{token}")
    public ResponseEntity<TripResponse> getTripByShareToken(
            @PathVariable String token) {
        return ResponseEntity.ok(
                tripService.getTripByShareToken(token));
    }

    // PUT /api/v1/trips/{tripId}/days/{dayId} — Update day
    @PutMapping("/{tripId}/days/{dayId}")
    public ResponseEntity<TripDayResponse> updateDay(
            @PathVariable Long tripId,
            @PathVariable Long dayId,
            @RequestBody TripDayResponse request) {
        return ResponseEntity.ok(
                tripService.updateTripDay(tripId, dayId, request));
    }

    // POST /api/v1/trips/{id}/days — Add day (append only)
    @PostMapping("/{id}/days")
    public ResponseEntity<TripResponse> addDay(
            @PathVariable Long id,
            @Valid @RequestBody AddDayRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("[DIAGNOSTIC-CONTROLLER] POST /api/v1/trips/{}/days REACHED. Authenticated user: {}",
                id, userDetails != null ? userDetails.getUsername() : "NONE");
        return ResponseEntity.ok(tripService.addDayToTrip(id, request));
    }

    // DELETE /api/v1/trips/{tripId}/days/{dayId} — Remove day (last day only)
    @DeleteMapping("/{tripId}/days/{dayId}")
    public ResponseEntity<TripResponse> removeDay(
            @PathVariable Long tripId,
            @PathVariable Long dayId) {
        return ResponseEntity.ok(tripService.removeLastDay(tripId, dayId));
    }

    // PATCH /api/v1/trips/{id}/title
    @PatchMapping("/{id}/title")
    public ResponseEntity<TripResponse> updateTitle(
        @PathVariable Long id,
        @RequestBody @Valid UpdateTripTitleRequest req) {
        return ResponseEntity.ok(tripService.updateTripTitle(id, req.getTitle()));
    }

    // POST /api/v1/trips/{tripId}/days/{dayId}/items — Add item
    @PostMapping("/{tripId}/days/{dayId}/items")
    public ResponseEntity<TripDayItemResponse> addItem(
            @PathVariable Long tripId,
            @PathVariable Long dayId,
            @RequestBody TripDayItemRequest request) {
        return ResponseEntity.ok(
                tripService.addItemToDay(dayId, request));
    }

    // DELETE /api/v1/trips/{tripId}/days/{dayId}/items/{itemId}
    @DeleteMapping("/{tripId}/days/{dayId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long tripId,
            @PathVariable Long dayId,
            @PathVariable Long itemId) {
        tripService.removeItemFromDay(dayId, itemId);
        return ResponseEntity.ok().build();
    }

    // PATCH /api/v1/trips/{id}/status
    @PatchMapping("/{id}/status")
    public ResponseEntity<TripResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(
                tripService.updateTripStatus(id, status));
    }

    // DELETE /api/v1/trips/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(
            @PathVariable Long id) {
        tripService.deleteTrip(id);
        return ResponseEntity.ok().build();
    }

    // POST /api/v1/trips/{id}/generate-ai
    @PostMapping("/{id}/generate-ai")
    public ResponseEntity<TripResponse> generateAiItinerary(
            @PathVariable Long id,
            @RequestBody GenerateAiTripRequest request) {
        request.setTripId(id);
        return ResponseEntity.ok(
                tripService.generateAiItinerary(request));
    }

    // GET /api/v1/trips/{id}/syncable-bookings
    @GetMapping("/{id}/syncable-bookings")
    public ResponseEntity<List<SyncableBookingResponse>> getSyncableBookings(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                budgetService.getSyncableBookings(id));
    }

    // POST /api/v1/trips/{id}/budget/sync-booking/{type}/{bookingId}
    @PostMapping("/{id}/budget/sync-booking/{type}/{bookingId}")
    public ResponseEntity<com.exploreceylon.backend.dto.budget.BudgetResponse> syncBookingToBudget(
            @PathVariable Long id,
            @PathVariable String type,
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(
                budgetService.syncBookingToTripBudget(id, type, bookingId));
    }
}