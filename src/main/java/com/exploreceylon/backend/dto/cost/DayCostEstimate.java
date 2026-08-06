package com.exploreceylon.backend.dto.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Daily cost estimate DTO encapsulating day number, date, breakdown, and total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayCostEstimate {
    private int dayNumber;
    private LocalDate date;
    private CostBreakdown breakdown;
    private double totalDayCost;
}
