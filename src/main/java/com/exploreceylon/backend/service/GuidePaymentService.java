package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.payment.*;
import com.exploreceylon.backend.exception.UnauthenticatedException;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.model.GuidePayment.PaymentPhase;
import com.exploreceylon.backend.model.GuidePayment.PaymentStatus;
import com.exploreceylon.backend.model.GuideBooking.BookingStatus;
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
public class GuidePaymentService {

    private final GuidePaymentRepository   guidePaymentRepo;
    private final GuideBookingRepository   guideBookingRepo;
    private final UserRepository           userRepo;
    private final PayHereService           payHereService;

    // ── Step 1: Initiate payment → build PayHere form ─────
    @Transactional
    public PayHereInitResponse initiatePayment(PayHereInitRequest req) {
        User user = getCurrentUser();

        GuideBooking booking = guideBookingRepo.findById(req.getBookingId())
            .orElseThrow(() -> new RuntimeException("Guide booking not found: " + req.getBookingId()));

        // Ownership check
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your booking");
        }

        // Check not already paid for this phase
        PaymentPhase phase = PaymentPhase.valueOf(req.getPaymentPhase());
        boolean alreadyPaid = guidePaymentRepo.existsByGuideBookingIdAndPaymentPhaseAndStatus(
            booking.getId(), phase, PaymentStatus.COMPLETED
        );
        if (alreadyPaid) throw new RuntimeException("This payment phase is already completed");

        // Calculate amount
        double phaseAmount = payHereService.calcPhaseAmount(booking.getTotalCost(), req.getPaymentPhase());

        // Fill customer info if missing
        if (req.getFirstName() == null) req.setFirstName(user.getName().split(" ")[0]);
        if (req.getLastName()  == null) req.setLastName(user.getName().contains(" ") ? user.getName().split(" ", 2)[1] : "-");
        if (req.getEmail()     == null) req.setEmail(user.getEmail());
        if (req.getPhone()     == null) req.setPhone(user.getPhone() != null ? user.getPhone() : "N/A");
        req.setBookingType("GUIDE");

        // Create PENDING payment record
        String orderId = payHereService.generateOrderId("GUIDE", booking.getId(), req.getPaymentPhase());

        GuidePayment payment = GuidePayment.builder()
            .guideBooking(booking)
            .user(user)
            .paymentPhase(phase)
            .phasePercent(payHereService.phasePercent(req.getPaymentPhase()))
            .amount(phaseAmount)
            .commissionAmount(payHereService.calculateCommission(phaseAmount))
            .guidePayout(payHereService.calculatePayout(phaseAmount))
            .currency("USD")
            .payhereOrderId(orderId)
            .status(PaymentStatus.PENDING)
            .build();
        guidePaymentRepo.save(payment);

        log.info("Guide payment initiated: orderId={}, amount={}", orderId, phaseAmount);

        // Build PayHere form response
        String itemName = "Guide Booking — " + booking.getId() + " (" + req.getPaymentPhase() + " " + payHereService.phasePercent(req.getPaymentPhase()) + "%)";
        return payHereService.buildInitResponse(req, orderId, phaseAmount, itemName);
    }

    // ── Step 2: Handle PayHere webhook (IPN) ─────────────
    @Transactional
    public void handleNotification(PayHereNotifyRequest notify) {
        log.info("Guide payment notify received: orderId={}, status={}", notify.getOrder_id(), notify.getStatus_code());

        // 1. Verify signature
        if (!payHereService.verifyNotification(notify)) {
            log.warn("Invalid PayHere signature — ignoring");
            return;
        }

        // 2. Find pending payment
        GuidePayment payment = guidePaymentRepo.findByPayhereOrderId(notify.getOrder_id())
            .orElseThrow(() -> new RuntimeException("Payment not found for order: " + notify.getOrder_id()));

        // 3. Update payment status
        payment.setPayherePaymentId(notify.getPayment_id());
        payment.setPayhereStatusCode(notify.getStatus_code());

        if (payHereService.isPaymentSuccess(notify.getStatus_code())) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());

            // 4. Update booking status
            GuideBooking booking = payment.getGuideBooking();
            if (payment.getPaymentPhase() == PaymentPhase.ADVANCE) {
                booking.setStatus(BookingStatus.CONFIRMED);
                log.info("Guide booking {} → CONFIRMED (20% advance paid)", booking.getId());
            } else {
                booking.setStatus(BookingStatus.COMPLETED);
                log.info("Guide booking {} → COMPLETED (80% balance paid)", booking.getId());
            }
            guideBookingRepo.save(booking);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Guide payment FAILED for order: {}", notify.getOrder_id());
        }
        guidePaymentRepo.save(payment);
    }

    // ── Step 2b: Confirm payment from the browser return_url ──────────
    // Fallback for local/sandbox setups where PayHere's server-to-server
    // IPN can't reach a non-public notify_url (e.g. localhost). Called by
    // the frontend success page right after PayHere redirects the user back.
    // Idempotent — safe to call even if the real IPN already completed it.
    @Transactional
    public PaymentResponse confirmPayment(String orderId) {
        User user = getCurrentUser();

        GuidePayment payment = guidePaymentRepo.findByPayhereOrderId(orderId)
            .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your payment");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());

            GuideBooking booking = payment.getGuideBooking();
            if (payment.getPaymentPhase() == PaymentPhase.ADVANCE) {
                booking.setStatus(BookingStatus.CONFIRMED);
                log.info("Guide booking {} → CONFIRMED (20% advance confirmed via return_url)", booking.getId());
            } else {
                booking.setStatus(BookingStatus.COMPLETED);
                log.info("Guide booking {} → COMPLETED (80% balance confirmed via return_url)", booking.getId());
            }
            guideBookingRepo.save(booking);
            guidePaymentRepo.save(payment);
        }
        return toResponse(payment);
    }

    // ── Get payments for a booking ────────────────────────
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForBooking(Long bookingId) {
        return guidePaymentRepo.findByGuideBookingId(bookingId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Get all payments for current user ─────────────────
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyGuidePayments() {
        User user = getCurrentUser();
        return guidePaymentRepo.findByUserId(user.getId())
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Mapper ────────────────────────────────────────────
    private PaymentResponse toResponse(GuidePayment p) {
        return PaymentResponse.builder()
            .id(p.getId())
            .bookingType("GUIDE")
            .bookingId(p.getGuideBooking().getId())
            .paymentPhase(p.getPaymentPhase().name())
            .phasePercent(p.getPhasePercent())
            .amount(p.getAmount())
            .commissionAmount(p.getCommissionAmount())
            .payout(p.getGuidePayout())
            .currency(p.getCurrency())
            .status(p.getStatus().name())
            .payhereOrderId(p.getPayhereOrderId())
            .paidAt(p.getPaidAt())
            .createdAt(p.getCreatedAt())
            .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email).orElseThrow(() -> new UnauthenticatedException("User not found"));
    }
}