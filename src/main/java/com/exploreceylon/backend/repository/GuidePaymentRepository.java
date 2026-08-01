package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.GuidePayment;
import com.exploreceylon.backend.model.GuidePayment.PaymentPhase;
import com.exploreceylon.backend.model.GuidePayment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuidePaymentRepository extends JpaRepository<GuidePayment, Long> {

    // All payments for a booking
    List<GuidePayment> findByGuideBookingId(Long bookingId);

    // Specific phase payment
    Optional<GuidePayment> findByGuideBookingIdAndPaymentPhase(
        Long bookingId, PaymentPhase phase
    );

    // All payments by traveler
    List<GuidePayment> findByUserId(Long userId);

    // By PayHere order ID (for webhook)
    Optional<GuidePayment> findByPayhereOrderId(String orderId);

    // Total revenue from guide payments
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM GuidePayment p " +
           "WHERE p.status = :status")
    Double sumAmountByStatus(@Param("status") PaymentStatus status);

    // Total commission earned
    @Query("SELECT COALESCE(SUM(p.commissionAmount), 0) FROM GuidePayment p " +
           "WHERE p.status = 'COMPLETED'")
    Double totalCommissionEarned();

    // Check if advance already paid
    boolean existsByGuideBookingIdAndPaymentPhaseAndStatus(
        Long bookingId, PaymentPhase phase, PaymentStatus status
    );
}