package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.payment.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.model.VehiclePayment.PaymentPhase;
import com.exploreceylon.backend.model.VehiclePayment.PaymentStatus;
import com.exploreceylon.backend.model.VehicleBooking.BookingStatus;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehiclePaymentService {

    private final VehiclePaymentRepository vehiclePaymentRepo;
    private final VehicleBookingRepository vehicleBookingRepo;
    private final UserRepository           userRepo;
    private final PayHereService           payHereService;

    // ── Step 1: Initiate payment ──────────────────────────
    @Transactional
    public PayHereInitResponse initiatePayment(PayHereInitRequest req) {
        User user = getCurrentUser();

        VehicleBooking booking = vehicleBookingRepo.findById(req.getBookingId())
            .orElseThrow(() -> new RuntimeException("Vehicle booking not found: " + req.getBookingId()));

        if (!booking.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your booking");
        }

        PaymentPhase phase = PaymentPhase.valueOf(req.getPaymentPhase());
        boolean alreadyPaid = vehiclePaymentRepo.existsByVehicleBookingIdAndPaymentPhaseAndStatus(
            booking.getId(), phase, PaymentStatus.COMPLETED
        );
        if (alreadyPaid) throw new RuntimeException("This payment phase is already completed");

        double phaseAmount = payHereService.calcPhaseAmount(booking.getTotalCost(), req.getPaymentPhase());

        if (req.getFirstName() == null) req.setFirstName(user.getName().split(" ")[0]);
        if (req.getLastName()  == null) req.setLastName(user.getName().contains(" ") ? user.getName().split(" ", 2)[1] : "-");
        if (req.getEmail()     == null) req.setEmail(user.getEmail());
        if (req.getPhone()     == null) req.setPhone(user.getPhone() != null ? user.getPhone() : "N/A");
        req.setBookingType("VEHICLE");

        String orderId = payHereService.generateOrderId("VEHICLE", booking.getId(), req.getPaymentPhase());

        VehiclePayment payment = VehiclePayment.builder()
            .vehicleBooking(booking)
            .user(user)
            .paymentPhase(phase)
            .phasePercent(payHereService.phasePercent(req.getPaymentPhase()))
            .amount(phaseAmount)
            .commissionAmount(payHereService.calculateCommission(phaseAmount))
            .driverPayout(payHereService.calculatePayout(phaseAmount))
            .currency("USD")
            .payhereOrderId(orderId)
            .status(PaymentStatus.PENDING)
            .build();
        vehiclePaymentRepo.save(payment);

        log.info("Vehicle payment initiated: orderId={}, amount={}", orderId, phaseAmount);

        String itemName = "Vehicle Booking — " + booking.getId() + " (" + req.getPaymentPhase() + " " + payHereService.phasePercent(req.getPaymentPhase()) + "%)";
        return payHereService.buildInitResponse(req, orderId, phaseAmount, itemName);
    }

    // ── Step 2: Handle PayHere webhook ────────────────────
    @Transactional
    public void handleNotification(PayHereNotifyRequest notify) {
        log.info("Vehicle payment notify received: orderId={}, status={}", notify.getOrder_id(), notify.getStatus_code());

        if (!payHereService.verifyNotification(notify)) {
            log.warn("Invalid PayHere signature — ignoring");
            return;
        }

        VehiclePayment payment = vehiclePaymentRepo.findByPayhereOrderId(notify.getOrder_id())
            .orElseThrow(() -> new RuntimeException("Payment not found for order: " + notify.getOrder_id()));

        payment.setPayherePaymentId(notify.getPayment_id());
        payment.setPayhereStatusCode(notify.getStatus_code());

        if (payHereService.isPaymentSuccess(notify.getStatus_code())) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());

            VehicleBooking booking = payment.getVehicleBooking();
            if (payment.getPaymentPhase() == PaymentPhase.ADVANCE) {
                booking.setStatus(BookingStatus.CONFIRMED);
                log.info("Vehicle booking {} → CONFIRMED (20% advance paid)", booking.getId());
            } else {
                booking.setStatus(BookingStatus.COMPLETED);
                log.info("Vehicle booking {} → COMPLETED (80% balance paid)", booking.getId());
            }
            vehicleBookingRepo.save(booking);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Vehicle payment FAILED for order: {}", notify.getOrder_id());
        }
        vehiclePaymentRepo.save(payment);
    }

    // ── Step 2b: Confirm payment from the browser return_url ──────────
    // Fallback for local/sandbox setups where PayHere's server-to-server
    // IPN can't reach a non-public notify_url (e.g. localhost). Called by
    // the frontend success page right after PayHere redirects the user back.
    // Idempotent — safe to call even if the real IPN already completed it.
    @Transactional
    public PaymentResponse confirmPayment(String orderId) {
        User user = getCurrentUser();

        VehiclePayment payment = vehiclePaymentRepo.findByPayhereOrderId(orderId)
            .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your payment");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());

            VehicleBooking booking = payment.getVehicleBooking();
            if (payment.getPaymentPhase() == PaymentPhase.ADVANCE) {
                booking.setStatus(BookingStatus.CONFIRMED);
                log.info("Vehicle booking {} → CONFIRMED (20% advance confirmed via return_url)", booking.getId());
            } else {
                booking.setStatus(BookingStatus.COMPLETED);
                log.info("Vehicle booking {} → COMPLETED (80% balance confirmed via return_url)", booking.getId());
            }
            vehicleBookingRepo.save(booking);
            vehiclePaymentRepo.save(payment);
        }
        return toResponse(payment);
    }

    // ── Get payments for a booking ────────────────────────
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForBooking(Long bookingId) {
        return vehiclePaymentRepo.findByVehicleBookingId(bookingId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Get all payments for current user ─────────────────
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyVehiclePayments() {
        User user = getCurrentUser();
        return vehiclePaymentRepo.findByUserId(user.getId())
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Mapper ────────────────────────────────────────────
    private PaymentResponse toResponse(VehiclePayment p) {
        return PaymentResponse.builder()
            .id(p.getId())
            .bookingType("VEHICLE")
            .bookingId(p.getVehicleBooking().getId())
            .paymentPhase(p.getPaymentPhase().name())
            .phasePercent(p.getPhasePercent())
            .amount(p.getAmount())
            .commissionAmount(p.getCommissionAmount())
            .payout(p.getDriverPayout())
            .currency(p.getCurrency())
            .status(p.getStatus().name())
            .payhereOrderId(p.getPayhereOrderId())
            .paidAt(p.getPaidAt())
            .createdAt(p.getCreatedAt())
            .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }
}