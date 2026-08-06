package com.exploreceylon.backend.dto.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed cost breakdown DTO for transport, entrance tickets, food, parking, and misc expenses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdown {
    private double transportCost;
    private double entranceTicketsCost;
    private double foodCost;
    private double hiddenGemsCost;
    private double parkingCost;
    private double miscCost;
    private double total;
}
