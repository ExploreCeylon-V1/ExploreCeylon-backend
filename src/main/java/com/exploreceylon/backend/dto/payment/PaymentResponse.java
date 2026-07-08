package com.exploreceylon.backend.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class PaymentResponse {
    private Long id;
    private String bookingType;      // GUIDE / VEHICLE
    private Long bookingId;
    private String paymentPhase;     // ADVANCE / FINAL
    private Integer phasePercent;    // 40 / 60
    private Double amount;
    private Double commissionAmount;
    private Double payout;
    private String currency;
    private String status;           // PENDING / COMPLETED / FAILED
    private String payhereOrderId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}