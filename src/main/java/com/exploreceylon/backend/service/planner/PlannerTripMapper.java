package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.planner.PlannerRequest;
import com.exploreceylon.backend.dto.planner.PlannerResponse;
import com.exploreceylon.backend.dto.planner.PlannerTripSummary;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.model.Trip.BudgetRange;
import com.exploreceylon.backend.model.Trip.TravelStyle;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedStop;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;

/**
 * Mapper component converting PlannerResponse and PlannerRequest into persistent Trip entities,
 * as well as Phase 13 PlannerMetadata and PlannerCostSnapshot.
 */
@Component
public class PlannerTripMapper {

    public Trip mapToEntity(PlannerRequest request, PlannerResponse response, User user) {
        if (request == null || response == null || user == null) {
            throw new IllegalArgumentException("Request, response, and user are required for mapping.");
        }

        LocalDate start = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        LocalDate end = start.plusDays(Math.max(0, request.getTripDays() - 1));
        double estBudget = response.getEstimatedCost() != null ? response.getEstimatedCost().getGrandTotal() : 0.0;

        Trip trip = Trip.builder()
                .user(user)
                .title(request.getOrigin() + " to " + request.getDestination() + " Trip")
                .fromLocation(request.getOrigin())
                .toLocation(request.getDestination())
                .startDate(start)
                .endDate(end)
                .travelStyle(parseTravelStyle(request.getTravelStyle()))
                .budgetRange(parseBudgetRange(request.getBudget()))
                .groupSize(request.getGroupSize())
                .budgetAmountLkr(estBudget) // Initializing Existing Budget Tracker Estimated Budget!
                .status(TripStatus.GENERATED)
                .aiGenerated(true)
                .days(new ArrayList<>())
                .build();

        if (response.getDays() != null) {
            for (PlannedDay pd : response.getDays()) {
                TripDay dayEntity = TripDay.builder()
                        .trip(trip)
                        .dayNumber(pd.dayNumber())
                        .date(pd.date())
                        .region(pd.region())
                        .theme("Day " + pd.dayNumber() + ": " + pd.region())
                        .estimatedDayCost(pd.estimatedDayCost())
                        .items(new ArrayList<>())
                        .build();

                if (pd.stops() != null) {
                    int order = 1;
                    for (PlannedStop s : pd.stops()) {
                        TripDayItem item = TripDayItem.builder()
                                .tripDay(dayEntity)
                                .type(s.type() == com.exploreceylon.backend.service.ItineraryAssemblyService.StopType.GEM ? TripDayItem.ItemType.GEM : TripDayItem.ItemType.ACTIVITY)
                                .referenceId(s.referenceId() != null ? String.valueOf(s.referenceId()) : null)
                                .title(s.name())
                                .cost(s.costUsd())
                                .notes("[" + s.slot() + "] " + s.name())
                                .orderIndex(order++)
                                .build();
                        dayEntity.getItems().add(item);
                    }
                }
                trip.getDays().add(dayEntity);
            }
        }

        return trip;
    }

    public PlannerMetadata mapToMetadata(PlannerResponse response, Trip trip) {
        if (response == null || trip == null) return null;
        double score = response.getQualityScore();
        long timeMs = response.getStatistics() != null ? response.getStatistics().getTotalPipelineExecutionTimeMs() : 0L;

        return PlannerMetadata.builder()
                .trip(trip)
                .plannerVersion("13.0")
                .qualityScore(score)
                .generationTimeMs(timeMs)
                .executionTimeMs(timeMs)
                .routeReuse(true)
                .aiProvider("GROQ / ExploreCeylon Narrative")
                .fallbackUsed(false)
                .versionNumber(1)
                .editCount(0)
                .build();
    }

    public PlannerCostSnapshot mapToCostSnapshot(PlannerResponse response, Trip trip) {
        if (response == null || response.getEstimatedCost() == null || trip == null) return null;
        var est = response.getEstimatedCost();
        var bd = est.getTotalBreakdown();

        return PlannerCostSnapshot.builder()
                .trip(trip)
                .grandTotal(est.getGrandTotal())
                .transportCost(bd != null ? bd.getTransportCost() : 0.0)
                .entranceTicketsCost(bd != null ? bd.getEntranceTicketsCost() : 0.0)
                .foodCost(bd != null ? bd.getFoodCost() : 0.0)
                .hiddenGemsCost(bd != null ? bd.getHiddenGemsCost() : 0.0)
                .parkingCost(bd != null ? bd.getParkingCost() : 0.0)
                .miscCost(bd != null ? bd.getMiscCost() : 0.0)
                .build();
    }

    public PlannerTripSummary mapToSummary(Trip trip) {
        if (trip == null) return null;
        return PlannerTripSummary.builder()
                .tripId(trip.getId())
                .title(trip.getTitle())
                .fromLocation(trip.getFromLocation())
                .toLocation(trip.getToLocation())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .status(trip.getStatus())
                .estimatedBudget(trip.getBudgetAmountLkr())
                .shareToken(trip.getShareToken())
                .createdAt(trip.getCreatedAt())
                .build();
    }

    public PlannerResponse mapEntityToResponse(Trip trip, PlannerMetadata metadata, PlannerCostSnapshot costSnapshot) {
        if (trip == null) return null;

        java.util.List<PlannedDay> plannedDays = new java.util.ArrayList<>();
        if (trip.getDays() != null) {
            for (TripDay td : trip.getDays()) {
                java.util.List<PlannedStop> stops = new java.util.ArrayList<>();
                if (td.getItems() != null) {
                    for (TripDayItem tdi : td.getItems()) {
                        com.exploreceylon.backend.service.ItineraryAssemblyService.StopType stopType = tdi.getType() == TripDayItem.ItemType.GEM
                                ? com.exploreceylon.backend.service.ItineraryAssemblyService.StopType.GEM
                                : com.exploreceylon.backend.service.ItineraryAssemblyService.StopType.DESTINATION;

                        Long refId = null;
                        if (tdi.getReferenceId() != null) {
                            try {
                                refId = Long.parseLong(tdi.getReferenceId());
                            } catch (NumberFormatException ignored) {}
                        }

                        stops.add(new PlannedStop(
                                stopType,
                                refId,
                                tdi.getTitle() != null ? tdi.getTitle() : "Attraction",
                                td.getRegion() != null ? td.getRegion() : "Sri Lanka",
                                0.0, 0.0, 60,
                                tdi.getCost() != null ? tdi.getCost() : 0.0,
                                "MORNING"
                        ));
                    }
                }

                plannedDays.add(new PlannedDay(
                        td.getDayNumber() != null ? td.getDayNumber() : 1,
                        td.getDate() != null ? td.getDate() : trip.getStartDate(),
                        td.getRegion() != null ? td.getRegion() : "Sri Lanka",
                        stops,
                        td.getEstimatedDayCost() != null ? td.getEstimatedDayCost() : 0.0
                ));
            }
        }

        int tripDays = (int) (trip.getEndDate().toEpochDay() - trip.getStartDate().toEpochDay() + 1);

        com.exploreceylon.backend.dto.planner.PlannerSummary summary = com.exploreceylon.backend.dto.planner.PlannerSummary.builder()
                .origin(trip.getFromLocation())
                .destination(trip.getToLocation())
                .tripDays(tripDays)
                .travelStyle(trip.getTravelStyle() != null ? trip.getTravelStyle().name() : "RELAXED")
                .budget(trip.getBudgetRange() != null ? trip.getBudgetRange().name() : "MID_RANGE")
                .groupSize(trip.getGroupSize() != null ? trip.getGroupSize() : 1)
                .overallScore(metadata != null && metadata.getQualityScore() != null ? metadata.getQualityScore() : 95.0)
                .build();

        com.exploreceylon.backend.dto.cost.TripCostEstimate costEstimate;
        if (costSnapshot != null) {
            costEstimate = com.exploreceylon.backend.dto.cost.TripCostEstimate.builder()
                    .grandTotal(costSnapshot.getGrandTotal())
                    .build();
        } else {
            costEstimate = com.exploreceylon.backend.dto.cost.TripCostEstimate.builder()
                    .grandTotal(trip.getBudgetAmountLkr() != null ? trip.getBudgetAmountLkr() : 0.0)
                    .build();
        }

        com.exploreceylon.backend.dto.planner.PlannerStatistics statistics = com.exploreceylon.backend.dto.planner.PlannerStatistics.builder()
                .totalPipelineExecutionTimeMs(metadata != null && metadata.getExecutionTimeMs() != null ? metadata.getExecutionTimeMs() : 100L)
                .totalDestinationsEvaluated(countStopsInList(plannedDays))
                .totalStopsScheduled(countStopsInList(plannedDays))
                .routeMatrixReusePercentage(100.0)
                .build();

        com.exploreceylon.backend.dto.narrative.NarrativeResponse narrative = com.exploreceylon.backend.dto.narrative.NarrativeResponse.builder()
                .tripOverview("Your saved journey from " + trip.getFromLocation() + " to " + trip.getToLocation())
                .build();

        return PlannerResponse.builder()
                .tripId(trip.getId())
                .createdAt(trip.getCreatedAt())
                .owner(trip.getUser() != null ? trip.getUser().getEmail() : null)
                .status(trip.getStatus())
                .summary(summary)
                .days(plannedDays)
                .destinations(java.util.List.of(trip.getToLocation() != null ? trip.getToLocation() : "Sri Lanka"))
                .gems(java.util.List.of())
                .events(java.util.List.of())
                .narrative(narrative)
                .estimatedCost(costEstimate)
                .statistics(statistics)
                .qualityScore(summary.getOverallScore())
                .build();
    }

    private int countStopsInList(java.util.List<PlannedDay> days) {
        if (days == null) return 0;
        return days.stream().mapToInt(d -> d.stops() != null ? d.stops().size() : 0).sum();
    }

    private TravelStyle parseTravelStyle(String style) {
        if (style == null) return TravelStyle.RELAXATION;
        try {
            return TravelStyle.valueOf(style.toUpperCase());
        } catch (Exception e) {
            return TravelStyle.RELAXATION;
        }
    }

    private BudgetRange parseBudgetRange(String budget) {
        if (budget == null) return BudgetRange.MID_RANGE;
        try {
            return BudgetRange.valueOf(budget.toUpperCase());
        } catch (Exception e) {
            return BudgetRange.MID_RANGE;
        }
    }
}
