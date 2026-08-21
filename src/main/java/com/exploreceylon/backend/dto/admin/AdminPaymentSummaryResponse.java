package com.exploreceylon.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentSummaryResponse {
    private Double totalRevenueCollected; // Total USD collected across all completed payments
    private Long partial20Count;          // Total confirmed bookings with 20% advance paid
    private Long full100Count;            // Total completed bookings with 100% settled
    private Long overdueCount;            // Total confirmed bookings where balance payment is overdue
}
