package com.exploreceylon.backend.service.matrix;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.selection.SelectedStop;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service generating realistic, clock-synchronized daily timelines using pre-computed RouteMatrix.
 * Automatically handles Lunch (12:00-14:00) and Tea (15:00-17:00) break insertions.
 */
@Service
@Slf4j
public class TimelineOptimizer {

    private final VisitDurationEstimator visitDurationEstimator;

    public TimelineOptimizer(VisitDurationEstimator visitDurationEstimator) {
        this.visitDurationEstimator = visitDurationEstimator;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledTimeline {
        private int dayNumber;
        private String dayStartTime;
        private String dayEndTime;
        private List<SelectedStop> scheduledStops;
        private boolean lunchInserted;
        private boolean teaInserted;
    }

    /**
     * Optimizes and formats a daily itinerary into a clock-synchronized timeline.
     *
     * @param day             TripDay containing allocated destinations
     * @param matrix          Pre-computed RouteMatrix
     * @param dayStartMinutes Minutes from midnight for day start (e.g. 480 = 08:00 AM)
     * @param dayStartPoint   Origin or start coordinate for the day
     * @return ScheduledTimeline DTO
     */
    public ScheduledTimeline optimizeTimeline(TripDay day, RouteMatrix matrix, int dayStartMinutes, GeoPoint dayStartPoint) {
        if (day == null || day.destinations() == null || day.destinations().isEmpty()) {
            return ScheduledTimeline.builder()
                    .dayNumber(day != null ? day.dayNumber() : 1)
                    .dayStartTime(formatClock(dayStartMinutes))
                    .dayEndTime(formatClock(dayStartMinutes))
                    .scheduledStops(List.of())
                    .lunchInserted(false)
                    .teaInserted(false)
                    .build();
        }

        List<SelectedStop> scheduledStops = new ArrayList<>();
        int currentClock = dayStartMinutes; // e.g. 480 (08:00 AM)
        GeoPoint prevPos = dayStartPoint != null ? dayStartPoint : new GeoPoint(day.destinations().get(0).getLatitude(), day.destinations().get(0).getLongitude());

        boolean lunchInserted = false;
        boolean teaInserted = false;
        int seq = 1;

        for (Destination d : day.destinations()) {
            GeoPoint currPos = new GeoPoint(d.getLatitude(), d.getLongitude());

            double travelDistKm = matrix != null ? matrix.getEntry(prevPos, currPos).getDistanceKm() : 0.0;
            int travelMin = matrix != null ? (int) Math.round(matrix.getEntry(prevPos, currPos).getDurationMinutes()) : 15;

            currentClock += travelMin;

            // Check Lunch Window (12:00 PM - 02:00 PM / 720 - 840 min)
            if (!lunchInserted && currentClock >= 720 && currentClock <= 840) {
                currentClock += 60; // 60 min lunch break
                lunchInserted = true;
            }

            // Check Tea Window (03:00 PM - 05:00 PM / 900 - 1020 min)
            if (!teaInserted && currentClock >= 900 && currentClock <= 1020) {
                currentClock += 20; // 20 min tea break
                teaInserted = true;
            }

            String arrTimeStr = formatClock(currentClock);
            int visitMin = visitDurationEstimator.estimateMinutes(d.getCategory());
            currentClock += visitMin;
            String depTimeStr = formatClock(currentClock);

            scheduledStops.add(SelectedStop.builder()
                    .destination(d)
                    .sequenceIndex(seq++)
                    .arrivalTime(arrTimeStr)
                    .departureTime(depTimeStr)
                    .visitDurationMinutes(visitMin)
                    .travelDurationMinutesFromPrevious(travelMin)
                    .travelDistanceKmFromPrevious(travelDistKm)
                    .category(d.getCategory() != null ? d.getCategory().name() : "GENERAL")
                    .build());

            prevPos = currPos;
        }

        return ScheduledTimeline.builder()
                .dayNumber(day.dayNumber())
                .dayStartTime(formatClock(dayStartMinutes))
                .dayEndTime(formatClock(currentClock))
                .scheduledStops(scheduledStops)
                .lunchInserted(lunchInserted)
                .teaInserted(teaInserted)
                .build();
    }

    private String formatClock(int totalMinutes) {
        int hours = (totalMinutes / 60) % 24;
        int mins = totalMinutes % 60;
        String ampm = hours >= 12 ? "PM" : "AM";
        int displayHour = hours % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format("%02d:%02d %s", displayHour, mins, ampm);
    }
}
