package com.exploreceylon.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentResponse {
    private Long bookingId;
    private String bookingType;          // "VEHICLE" | "GUIDE"
    private String bookingStatus;        // "CONFIRMED" | "COMPLETED"
    
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    
    private Long providerId;
    private String providerName;         // Vehicle Name or Guide Full Name
    
    private Long tripId;
    private String tripTitle;
    
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate paymentDueDate;    // endDate / dropoffDate
    
    private Double totalCost;
    private Double advanceAmount;        // 20%
    private Double balanceAmount;        // 80%
    private Double paidAmount;           // Authoritative amount paid to date
    private Double remainingBalance;     // 0 if COMPLETED, balanceAmount if CONFIRMED
    private String currency;             // "USD"
    
    private String paymentCompletion;    // "20%" | "100%"
    private String completionStatus;     // "PARTIAL_20" | "FULL_100"
    
    private LocalDateTime initialPaymentDate; // Timestamp of completed 20% advance
    private LocalDateTime finalPaymentDate;   // Timestamp of completed 80% final (null if unpaid)
    
    private boolean isOverdue;           // True if CONFIRMED and paymentDueDate < today
    private LocalDateTime createdAt;
}
