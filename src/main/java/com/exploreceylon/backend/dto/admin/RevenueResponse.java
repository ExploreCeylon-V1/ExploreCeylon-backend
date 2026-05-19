package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RevenueResponse {
    private Double totalRevenue;
    private Double vehicleRevenue;
    private Double guideRevenue;
    private Double totalCommission;
    private Double thisMonthRevenue;
    private Double commissionRate;
    private Map<String, Double> monthlyBreakdown;
    private List<MonthlyData> monthlyData;

    @Data
    @Builder
    public static class MonthlyData {
        private String month;
        private Double vehicleRevenue;
        private Double guideRevenue;
        private Double total;
    }
}