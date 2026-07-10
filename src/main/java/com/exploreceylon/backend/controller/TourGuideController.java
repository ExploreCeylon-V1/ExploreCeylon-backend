package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.guide.*;
import com.exploreceylon.backend.model.GuidePayment;
import com.exploreceylon.backend.repository.GuideBookingRepository;
import com.exploreceylon.backend.repository.GuidePaymentRepository;
import com.exploreceylon.backend.service.TourGuideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class TourGuideController {

    private final TourGuideService guideService;
    private final GuidePaymentRepository paymentRepository;
    private final GuideBookingRepository bookingRepository; 

    // ── Guide Endpoints ────────────────────────────────────

    // GET /api/v1/guides
    // startDate/endDate (optional, both required together) exclude guides with
    // an active booking overlapping that range, so only guides free for the
    // traveler's trip dates are returned.
    @GetMapping("/api/v1/guides")
    public ResponseEntity<List<GuideResponse>> getAllGuides(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
                guideService.getAllGuides(
                        district, language, specialty, maxPrice, startDate, endDate));
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

    // GET /api/v1/guides/{id}/availability?startDate&endDate → {available}
    @GetMapping("/api/v1/guides/{id}/availability")
    public ResponseEntity<Map<String, Boolean>> checkAvailability(
            @PathVariable Long id,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        boolean available = guideService.checkAvailability(id, startDate, endDate);
        return ResponseEntity.ok(Map.of("available", available));
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

    // GET /api/v1/guide-bookings/{id}
    @GetMapping("/api/v1/guide-bookings/{id}")
    public ResponseEntity<GuideBookingResponse> getBookingById(
            @PathVariable Long id) {
        return ResponseEntity.ok(guideService.getBookingById(id));
    }

    // GET /api/v1/guides/{id}/bookings
    @GetMapping("/api/v1/guides/{id}/bookings")
    public ResponseEntity<List<GuideBookingResponse>> getGuideBookings(
            @PathVariable Long id) {
        return ResponseEntity.ok(guideService.getGuideBookingsByGuideId(id));
    }

    // PATCH /api/v1/guide-bookings/{id}/cancel
    @PatchMapping("/api/v1/guide-bookings/{id}/cancel")
    public ResponseEntity<GuideBookingResponse> cancelBooking(
            @PathVariable Long id) {
        return ResponseEntity.ok(guideService.cancelBooking(id));
    }

    // PATCH /api/v1/guide-bookings/{id}/status (Admin)
    @PatchMapping("/api/v1/guide-bookings/{id}/status")
    public ResponseEntity<GuideBookingResponse> updateBookingStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(guideService.updateBookingStatus(id, status));
    }

    /* ─────────────────────────────────────────────────────────────────────
     * TEMPORARILY DISABLED — 2026-07-06
     * These admin guide-payout endpoints depended on the OLD GuidePayment model
     * (guideId / totalEarned / amountPaid / paymentDate / status PAID|UNPAID).
     * The payment refactor repurposed GuidePayment into a per-booking traveler
     * payment (guideBooking / amount / guidePayout / phases / PENDING|COMPLETED),
     * so this admin payout ledger now needs its own model. Rebuild in the
     * payment pass. Commented out to keep the backend compiling.

    // GET /api/v1/guide-payments/completed-summary
    @GetMapping("/api/v1/guide-payments/completed-summary")
    public ResponseEntity<List<Map<String, Object>>> getCompletedSummary() {
        // All guides ගන්නවා
        List<GuideResponse> guides = guideService.getAllGuides(null, null, null, null);
        String today = LocalDate.now().toString();

        List<Map<String, Object>> summaries = new ArrayList<>();

        for (GuideResponse guide : guides) {
            // ඒ guide ගේ completed bookings (CONFIRMED + endDate < today)
            List<GuideBookingResponse> bookings = guideService.getGuideBookingsByGuideId(guide.getId());
            List<GuideBookingResponse> completed = bookings.stream()
                .filter(b -> "CONFIRMED".equals(b.getStatus()) && b.getEndDate().toString().compareTo(today) < 0)
                .toList();

            if (completed.isEmpty()) continue;

            double totalEarned = completed.stream().mapToDouble(GuideBookingResponse::getTotalCost).sum();
            double commission = Math.round(totalEarned * 0.15 * 100.0) / 100.0;
            double amountPaid = Math.round(totalEarned * 0.85 * 100.0) / 100.0;

            Optional<GuidePayment> existingPayment = paymentRepository.findByGuideId(guide.getId());

            Map<String, Object> summary = new HashMap<>();
            summary.put("guideId", guide.getId());
            summary.put("guideName", guide.getFullName());
            summary.put("bookingIds", completed.stream().map(GuideBookingResponse::getId).toList());
            summary.put("totalEarned", totalEarned);
            summary.put("commissionDeducted", commission);
            summary.put("amountPaid", amountPaid);
            summary.put("paymentStatus", existingPayment.map(p -> p.getStatus().name()).orElse("UNPAID"));
            summary.put("paymentDate", existingPayment.map(p -> p.getPaymentDate().toString()).orElse(null));
            summary.put("paymentRecordId", existingPayment.map(GuidePayment::getId).orElse(null));

            summaries.add(summary);
        }

        return ResponseEntity.ok(summaries);
    }

    // POST /api/v1/guide-payments/{guideId}/mark-paid
    @PostMapping("/api/v1/guide-payments/{guideId}/mark-paid")
    public ResponseEntity<Map<String, Object>> markGuideAsPaid(
            @PathVariable Long guideId,
            @RequestBody Map<String, Object> payload) {

        GuidePayment payment = GuidePayment.builder()
            .guideId(guideId)
            .totalEarned(((Number) payload.get("totalEarned")).doubleValue())
            .commissionDeducted(((Number) payload.get("commissionDeducted")).doubleValue())
            .amountPaid(((Number) payload.get("amountPaid")).doubleValue())
            .paymentDate(LocalDate.parse((String) payload.get("paymentDate")))
            .status(GuidePayment.PaymentStatus.PAID)
            .build();

        GuidePayment saved = paymentRepository.save(payment);

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("guideId", saved.getGuideId());
        response.put("paymentDate", saved.getPaymentDate().toString());
        response.put("status", saved.getStatus().name());
        response.put("amountPaid", saved.getAmountPaid());

        return ResponseEntity.ok(response);
    }
    * ───────────────────────────────────────────────────────────────────── */
}
