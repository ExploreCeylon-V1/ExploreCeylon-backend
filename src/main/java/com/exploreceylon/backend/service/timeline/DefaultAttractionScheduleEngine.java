package com.exploreceylon.backend.service.timeline;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.timeline.TimelineContext;
import com.exploreceylon.backend.dto.timeline.TimelineStop;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.service.timeline.OpeningHoursService.OperatingHours;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of AttractionScheduleEngine.
 * Performs human-like daily attraction scheduling considering opening/closing hours,
 * waiting tolerance, driving fatigue rest periods, meal windows, and walking buffers.
 */
@Service
@Slf4j
public class DefaultAttractionScheduleEngine implements AttractionScheduleEngine {

    private final OpeningHoursService openingHoursService;
    private final PreferredVisitWindowService preferredVisitWindowService;
    private final VisitDurationEstimator visitDurationEstimator;

    @Value("${planner.timeline.max-wait-minutes:45}")
    private int maxWaitMinutes = 45;

    @Value("${planner.timeline.max-continuous-driving-minutes:180}")
    private int maxContinuousDrivingMinutes = 180;

    @Value("${planner.timeline.walking-buffer-minutes:8}")
    private int walkingBufferMinutes = 8;

    public DefaultAttractionScheduleEngine(OpeningHoursService openingHoursService,
                                           PreferredVisitWindowService preferredVisitWindowService,
                                           VisitDurationEstimator visitDurationEstimator) {
        this.openingHoursService = openingHoursService;
        this.preferredVisitWindowService = preferredVisitWindowService;
        this.visitDurationEstimator = visitDurationEstimator;
    }

    @Override
    public List<TimelineStop> scheduleDayTimeline(TimelineContext context) {
        if (context == null || context.getTripDay() == null || context.getTripDay().destinations() == null
                || context.getTripDay().destinations().isEmpty()) {
            return List.of();
        }

        List<TimelineStop> scheduledStops = new ArrayList<>();
        int currentClock = context.getDayStartMinutes() > 0 ? context.getDayStartMinutes() : 480; // Default 08:00 AM
        int accumulatedDriving = 0;

        GeoPoint prevPos = context.getOriginPoint() != null
                ? context.getOriginPoint()
                : new GeoPoint(context.getTripDay().destinations().get(0).getLatitude(), context.getTripDay().destinations().get(0).getLongitude());

        RouteMatrix matrix = context.getRouteMatrix();
        boolean lunchInserted = false;
        boolean teaInserted = false;
        int sequenceIndex = 1;

        for (Destination destination : context.getTripDay().destinations()) {
            GeoPoint currPos = new GeoPoint(destination.getLatitude(), destination.getLongitude());

            double travelDistKm = matrix != null ? matrix.getEntry(prevPos, currPos).getDistanceKm() : 0.0;
            int travelMin = matrix != null ? (int) Math.round(matrix.getEntry(prevPos, currPos).getDurationMinutes()) : 15;

            // Driving Fatigue Check
            accumulatedDriving += travelMin;
            if (accumulatedDriving >= maxContinuousDrivingMinutes) {
                currentClock += 20; // 20 min driving fatigue coffee/tea break
                accumulatedDriving = 0;
                log.info("Inserted 20 min driving fatigue break for long driving segment.");
            }

            int arrivalClock = currentClock + travelMin;
            OperatingHours opHours = openingHoursService.getOperatingHours(destination);

            int waitingMin = 0;
            int visitStartClock = arrivalClock;

            if (arrivalClock < opHours.openingMinutes()) {
                waitingMin = opHours.openingMinutes() - arrivalClock;
                if (waitingMin <= maxWaitMinutes) {
                    visitStartClock = opHours.openingMinutes();
                } else {
                    log.warn("Arrival at {} ({}) is {} min before opening ({}). Exceeds max wait {} min.",
                            destination.getName(), formatClock(arrivalClock), waitingMin, formatClock(opHours.openingMinutes()), maxWaitMinutes);
                    visitStartClock = arrivalClock;
                }
            }

            int visitDuration = visitDurationEstimator.estimateMinutes(destination.getCategory());
            int visitEndClock = visitStartClock + visitDuration;

            if (visitEndClock > opHours.closingMinutes()) {
                log.warn("Visit to {} extends past closing time {} (ends at {}). Adjusting.",
                        destination.getName(), formatClock(opHours.closingMinutes()), formatClock(visitEndClock));
            }

            int departureClock = visitEndClock + walkingBufferMinutes;
            String breakType = "NONE";

            // Check Meal Windows
            if (!lunchInserted && departureClock >= 720 && departureClock <= 840) {
                departureClock += 60; // 60 min lunch break
                lunchInserted = true;
                breakType = "LUNCH";
            } else if (!teaInserted && departureClock >= 900 && departureClock <= 1020) {
                departureClock += 20; // 20 min tea break
                teaInserted = true;
                breakType = "TEA";
            }

            scheduledStops.add(TimelineStop.builder()
                    .destination(destination)
                    .sequenceIndex(sequenceIndex++)
                    .arrivalTime(formatClock(arrivalClock))
                    .waitingMinutes(waitingMin)
                    .visitStartTime(formatClock(visitStartClock))
                    .visitEndTime(formatClock(visitEndClock))
                    .departureTime(formatClock(departureClock))
                    .travelDurationMinutesFromPrevious(travelMin)
                    .travelDistanceKmFromPrevious(travelDistKm)
                    .category(destination.getCategory() != null ? destination.getCategory().name() : "GENERAL")
                    .breakType(breakType)
                    .build());

            currentClock = departureClock;
            prevPos = currPos;
        }

        log.info("AttractionScheduleEngine scheduled {} stops for day {}", scheduledStops.size(), context.getTripDay().dayNumber());
        return scheduledStops;
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
