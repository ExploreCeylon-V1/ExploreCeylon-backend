package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.payment.*;
import com.exploreceylon.backend.service.VehiclePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments/vehicle")
@RequiredArgsConstructor
@Slf4j
public class VehiclePaymentController {

    private final VehiclePaymentService vehiclePaymentService;

    // POST /api/v1/payments/vehicle/initiate
    @PostMapping("/initiate")
    public ResponseEntity<PayHereInitResponse> initiatePayment(
        @RequestBody PayHereInitRequest request
    ) {
        PayHereInitResponse response = vehiclePaymentService.initiatePayment(request);
        return ResponseEntity.ok(response);
    }

    // POST /api/v1/payments/vehicle/notify
    @PostMapping("/notify")
    public ResponseEntity<Void> handleNotify(
        @ModelAttribute PayHereNotifyRequest notify
    ) {
        log.info("PayHere Vehicle IPN: order={}", notify.getOrder_id());
        vehiclePaymentService.handleNotification(notify);
        return ResponseEntity.ok().build();
    }

    // POST /api/v1/payments/vehicle/confirm/{orderId}
    // Fallback confirmation called from the frontend return_url page
    // when PayHere's IPN can't reach a local/non-public notify_url.
    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<PaymentResponse> confirmPayment(
        @PathVariable String orderId
    ) {
        return ResponseEntity.ok(vehiclePaymentService.confirmPayment(orderId));
    }

    // GET /api/v1/payments/vehicle/booking/{bookingId}
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<PaymentResponse>> getBookingPayments(
        @PathVariable Long bookingId
    ) {
        return ResponseEntity.ok(vehiclePaymentService.getPaymentsForBooking(bookingId));
    }

    // GET /api/v1/payments/vehicle/my
    @GetMapping("/my")
    public ResponseEntity<List<PaymentResponse>> getMyPayments() {
        return ResponseEntity.ok(vehiclePaymentService.getMyVehiclePayments());
    }
}