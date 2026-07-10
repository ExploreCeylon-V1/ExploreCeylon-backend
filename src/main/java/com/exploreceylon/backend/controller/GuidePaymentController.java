package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.payment.*;
import com.exploreceylon.backend.service.GuidePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments/guide")
@RequiredArgsConstructor
@Slf4j
public class GuidePaymentController {

    private final GuidePaymentService guidePaymentService;

    // ── Initiate payment → returns PayHere form data ──────
    // POST /api/v1/payments/guide/initiate
    @PostMapping("/initiate")
    public ResponseEntity<PayHereInitResponse> initiatePayment(
        @RequestBody PayHereInitRequest request
    ) {
        PayHereInitResponse response = guidePaymentService.initiatePayment(request);
        return ResponseEntity.ok(response);
    }

    // ── PayHere IPN webhook ───────────────────────────────
    // POST /api/v1/payments/guide/notify
    // Called by PayHere server (NOT the traveler)
    @PostMapping("/notify")
    public ResponseEntity<Void> handleNotify(
        @ModelAttribute PayHereNotifyRequest notify
    ) {
        log.info("PayHere Guide IPN: order={}", notify.getOrder_id());
        guidePaymentService.handleNotification(notify);
        return ResponseEntity.ok().build();
    }

    // ── Confirm payment (return_url fallback) ─────────────
    // POST /api/v1/payments/guide/confirm/{orderId}
    // Called from the frontend return_url page when PayHere's IPN
    // can't reach a local/non-public notify_url.
    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<PaymentResponse> confirmPayment(
        @PathVariable String orderId
    ) {
        return ResponseEntity.ok(guidePaymentService.confirmPayment(orderId));
    }

    // ── Get payments for a booking ────────────────────────
    // GET /api/v1/payments/guide/booking/{bookingId}
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<PaymentResponse>> getBookingPayments(
        @PathVariable Long bookingId
    ) {
        return ResponseEntity.ok(guidePaymentService.getPaymentsForBooking(bookingId));
    }

    // ── Get current user's guide payments ─────────────────
    // GET /api/v1/payments/guide/my
    @GetMapping("/my")
    public ResponseEntity<List<PaymentResponse>> getMyPayments() {
        return ResponseEntity.ok(guidePaymentService.getMyGuidePayments());
    }
}