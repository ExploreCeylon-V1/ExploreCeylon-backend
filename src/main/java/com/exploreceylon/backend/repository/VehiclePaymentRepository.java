package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.VehiclePayment;
import com.exploreceylon.backend.model.VehiclePayment.PaymentPhase;
import com.exploreceylon.backend.model.VehiclePayment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehiclePaymentRepository extends JpaRepository<VehiclePayment, Long> {

    List<VehiclePayment> findByVehicleBookingId(Long bookingId);

    Optional<VehiclePayment> findByVehicleBookingIdAndPaymentPhase(
        Long bookingId, PaymentPhase phase
    );

    List<VehiclePayment> findByUserId(Long userId);

    Optional<VehiclePayment> findByPayhereOrderId(String orderId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM VehiclePayment p " +
           "WHERE p.status = :status")
    Double sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.commissionAmount), 0) FROM VehiclePayment p " +
           "WHERE p.status = 'COMPLETED'")
    Double totalCommissionEarned();

    boolean existsByVehicleBookingIdAndPaymentPhaseAndStatus(
        Long bookingId, PaymentPhase phase, PaymentStatus status
    );
}