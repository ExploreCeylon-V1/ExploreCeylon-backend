package com.exploreceylon.backend.service.cost;

import com.exploreceylon.backend.dto.cost.*;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of TripCostEngine.
 * Performs pure, deterministic estimation of transport, food, entrance tickets, parking, and misc expenses
 * using configurable values from application.properties without any external API calls.
 */
@Service
@Slf4j
public class DefaultTripCostEngine implements TripCostEngine {

    @Value("${planner.cost.transport.per-km:45.0}")
    private double transportPerKm = 45.0;

    @Value("${planner.cost.food.relaxed:3500.0}")
    private double foodRelaxed = 3500.0;

    @Value("${planner.cost.food.balanced:2500.0}")
    private double foodBalanced = 2500.0;

    @Value("${planner.cost.food.fast:1800.0}")
    private double foodFast = 1800.0;

    @Value("${planner.cost.parking.daily:500.0}")
    private double parkingDaily = 500.0;

    @Value("${planner.cost.misc.daily:1000.0}")
    private double miscDaily = 1000.0;

    @Override
    public TripCostEstimate estimateTripCost(List<PlannedDay> plannedDays, RouteMatrix routeMatrix, String travelStyle, int groupSize) {
        if (plannedDays == null || plannedDays.isEmpty()) {
            return TripCostEstimate.builder()
                    .currency("LKR")
                    .dailyEstimates(List.of())
                    .grandTotal(0.0)
                    .build();
        }

        int travelers = Math.max(1, groupSize);
        double foodPerPerson = determineFoodCost(travelStyle);

        List<DayCostEstimate> dailyEstimates = new ArrayList<>();
        double grandTransport = 0.0;
        double grandTickets = 0.0;
        double grandFood = 0.0;
        double grandGems = 0.0;
        double grandParking = 0.0;
        double grandMisc = 0.0;
        double totalDistanceKm = 0.0;

        int highestCostDay = 1;
        double maxDayCost = -1.0;

        for (PlannedDay day : plannedDays) {
            double dayDistanceKm = 0.0;
            double dayTickets = 0.0;
            double dayGems = 0.0;

            if (day.stops() != null && !day.stops().isEmpty()) {
                GeoPoint prev = new GeoPoint(day.stops().get(0).lat(), day.stops().get(0).lng());
                for (PlannedStop stop : day.stops()) {
                    if (stop.lat() != null && stop.lng() != null) {
                        GeoPoint curr = new GeoPoint(stop.lat(), stop.lng());
                        if (routeMatrix != null) {
                            dayDistanceKm += routeMatrix.getEntry(prev, curr).getDistanceKm();
                        } else {
                            dayDistanceKm += 15.0; // Default fallback distance per stop
                        }
                        prev = curr;
                    }

                    if (stop.type() == com.exploreceylon.backend.service.ItineraryAssemblyService.StopType.GEM) {
                        dayGems += 500.0;
                    } else {
                        dayTickets += estimateTicketCost(stop.name());
                    }
                }
            }

            double dayTransport = Math.round(dayDistanceKm * transportPerKm * 10.0) / 10.0;
            double dayFood = foodPerPerson * travelers;
            double dayParking = parkingDaily;
            double dayMisc = miscDaily;

            double dayTotal = dayTransport + dayTickets + dayFood + dayGems + dayParking + dayMisc;

            if (dayTotal > maxDayCost) {
                maxDayCost = dayTotal;
                highestCostDay = day.dayNumber();
            }

            totalDistanceKm += dayDistanceKm;
            grandTransport += dayTransport;
            grandTickets += dayTickets;
            grandFood += dayFood;
            grandGems += dayGems;
            grandParking += dayParking;
            grandMisc += dayMisc;

            CostBreakdown dayBreakdown = CostBreakdown.builder()
                    .transportCost(dayTransport)
                    .entranceTicketsCost(dayTickets)
                    .foodCost(dayFood)
                    .hiddenGemsCost(dayGems)
                    .parkingCost(dayParking)
                    .miscCost(dayMisc)
                    .total(dayTotal)
                    .build();

            dailyEstimates.add(DayCostEstimate.builder()
                    .dayNumber(day.dayNumber())
                    .date(day.date())
                    .breakdown(dayBreakdown)
                    .totalDayCost(dayTotal)
                    .build());
        }

        double grandTotal = grandTransport + grandTickets + grandFood + grandGems + grandParking + grandMisc;

        CostBreakdown totalBreakdown = CostBreakdown.builder()
                .transportCost(grandTransport)
                .entranceTicketsCost(grandTickets)
                .foodCost(grandFood)
                .hiddenGemsCost(grandGems)
                .parkingCost(grandParking)
                .miscCost(grandMisc)
                .total(grandTotal)
                .build();

        CostStatistics statistics = CostStatistics.builder()
                .averageCostPerDay(Math.round((grandTotal / plannedDays.size()) * 10.0) / 10.0)
                .highestCostDayNumber(highestCostDay)
                .costPerKmRatio(totalDistanceKm > 0 ? Math.round((grandTotal / totalDistanceKm) * 10.0) / 10.0 : 0.0)
                .build();

        log.info("TripCostEngine calculated total cost: {} LKR for {} days", grandTotal, plannedDays.size());

        return TripCostEstimate.builder()
                .tripTitle("Estimated Trip Cost Breakdown")
                .currency("LKR")
                .dailyEstimates(dailyEstimates)
                .totalBreakdown(totalBreakdown)
                .grandTotal(grandTotal)
                .statistics(statistics)
                .build();
    }

    private double determineFoodCost(String travelStyle) {
        if (travelStyle == null) return foodBalanced;
        return switch (travelStyle.toUpperCase()) {
            case "RELAXED" -> foodRelaxed;
            case "FAST_PACED", "FAST" -> foodFast;
            default -> foodBalanced;
        };
    }

    private double estimateTicketCost(String name) {
        if (name == null) return 1000.0;
        String lower = name.toLowerCase();
        if (lower.contains("temple") || lower.contains("kovil") || lower.contains("dagoba")) return 1000.0;
        if (lower.contains("museum")) return 1500.0;
        if (lower.contains("zoo") || lower.contains("orphanage")) return 2000.0;
        if (lower.contains("botanical") || lower.contains("garden")) return 3000.0;
        if (lower.contains("waterfall") || lower.contains("falls")) return 500.0;
        if (lower.contains("beach")) return 0.0;
        if (lower.contains("adventure") || lower.contains("rafting")) return 4000.0;
        return 1000.0;
    }
}
