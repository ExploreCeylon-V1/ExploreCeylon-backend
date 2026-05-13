package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.guide.*;
import com.exploreceylon.backend.service.TourGuideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TourGuideController {

    private final TourGuideService guideService;

    // ── Guide Endpoints ────────────────────────────────────

    // GET /api/v1/guides
    @GetMapping("/api/v1/guides")
    public ResponseEntity<List<GuideResponse>> getAllGuides(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) Double maxPrice) {
        return ResponseEntity.ok(
                guideService.getAllGuides(
                        district, language, specialty, maxPrice));
    }

    // GET /api/v1/guides/{id}
    @GetMapping("/api/v1/guides/{id}")
    public ResponseEntity<GuideResponse> getGuideById(
            @PathVariable Long id) {
        return ResponseEntity.ok(guideService.getGuideById(id));
    }

    // GET /api/v1/guides/search
    @GetMapping("/api/v1/guides/search")
    public ResponseEntity<List<GuideResponse>> searchGuides(
            @RequestParam String keyword) {
        return ResponseEntity.ok(guideService.searchGuides(keyword));
    }

    // GET /api/v1/guides/{id}/reviews
    @GetMapping("/api/v1/guides/{id}/reviews")
    public ResponseEntity<List<ReviewResponse>> getReviews(
            @PathVariable Long id) {
        return ResponseEntity.ok(guideService.getGuideReviews(id));
    }

    // POST /api/v1/guides (Admin)
    @PostMapping("/api/v1/guides")
    public ResponseEntity<GuideResponse> addGuide(
            @Valid @RequestBody CreateGuideRequest request) {
        return ResponseEntity.ok(guideService.addGuide(request));
    }

    // PUT /api/v1/guides/{id} (Admin)
    @PutMapping("/api/v1/guides/{id}")
    public ResponseEntity<GuideResponse> updateGuide(
            @PathVariable Long id,
            @RequestBody CreateGuideRequest request) {
        return ResponseEntity.ok(
                guideService.updateGuide(id, request));
    }

    // PUT /api/v1/guides/{id}/availability (Admin)
    @PutMapping("/api/v1/guides/{id}/availability")
    public ResponseEntity<GuideResponse> toggleAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available) {
        return ResponseEntity.ok(
                guideService.toggleAvailability(id, available));
    }

    // DELETE /api/v1/guides/{id} (Admin)
    @DeleteMapping("/api/v1/guides/{id}")
    public ResponseEntity<Void> deleteGuide(@PathVariable Long id) {
        guideService.deleteGuide(id);
        return ResponseEntity.ok().build();
    }

    // ── Review Endpoints ───────────────────────────────────

    // POST /api/v1/guides/{id}/reviews
    @PostMapping("/api/v1/guides/{id}/reviews")
    public ResponseEntity<ReviewResponse> writeReview(
            @PathVariable Long id,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.ok(
                guideService.writeReview(id, request));
    }

    // ── Booking Endpoints ──────────────────────────────────

    // POST /api/v1/guide-bookings
    @PostMapping("/api/v1/guide-bookings")
    public ResponseEntity<GuideBookingResponse> bookGuide(
            @Valid @RequestBody BookGuideRequest request) {
        return ResponseEntity.ok(guideService.bookGuide(request));
    }

    // GET /api/v1/guide-bookings/my
    @GetMapping("/api/v1/guide-bookings/my")
    public ResponseEntity<List<GuideBookingResponse>> getMyBookings() {
        return ResponseEntity.ok(guideService.getMyBookings());
    }
}
