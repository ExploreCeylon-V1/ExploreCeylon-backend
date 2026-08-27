package com.exploreceylon.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentDetailResponse {

    // ── 1. Booking Information ────────────────────────────
    private Long bookingId;
    private String bookingType;          // "VEHICLE" | "GUIDE"
    private String bookingStatus;        // "CONFIRMED" | "COMPLETED"
    private LocalDateTime bookingCreatedAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private String pickupTime;
    private String dropoffTime;
    private String pickupLocation;
    private String dropoffLocation;
    private String notes;

    // ── 2. Customer Information ───────────────────────────
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    // ── 3. Provider Information ───────────────────────────
    private Long providerId;
    private String providerName;         // Vehicle Name or Guide Full Name
    private String driverName;           // Driver name for vehicle bookings
    private String providerPhone;
    private String providerEmail;
    private String providerDistrict;
    private String vehicleNumber;        // License plate for vehicle bookings
    private Double pricePerDay;
    private Map<String, Object> providerDetails; // Specific metadata (driver, seats, model, languages, specialties)

    // ── 4. Financial Breakdown ────────────────────────────
    private Double totalCost;
    private Double advanceAmount;        // Required 20%
    private Double balanceAmount;        // Required 80%
    private Double totalPaid;            // Sum of completed payments
    private Double remainingBalance;     // 0 if COMPLETED, balanceAmount if CONFIRMED
    private String currency;             // "USD"
    private String paymentCompletion;    // "20%" | "100%"
    private String completionStatus;     // "PARTIAL_20" | "FULL_100"

    // ── 5. Payment Phase Details ──────────────────────────
    private PhaseDetail initialPayment;
    private PhaseDetail finalPayment;

    // ── 6. Overdue State ──────────────────────────────────
    private boolean isOverdue;
    private LocalDate paymentDueDate;
    private Long daysOverdue;

    // ── 7. Related Trip ───────────────────────────────────
    private Long tripId;
    private String tripTitle;

    // ── 8. Notification History ───────────────────────────
    private Boolean reminderSent;
    private LocalDateTime lastReminderSentAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhaseDetail {
        private String phase;            // "ADVANCE" | "FINAL"
        private Integer percent;         // 20 | 80
        private Double amount;
        private String status;           // "COMPLETED" | "PENDING"
        private String payhereOrderId;
        private String payherePaymentId;
        private LocalDateTime paidAt;
        private String currency;
        private LocalDate dueDate;
    }
}
