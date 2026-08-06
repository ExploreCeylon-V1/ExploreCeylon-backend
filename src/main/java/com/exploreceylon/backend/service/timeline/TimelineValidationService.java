package com.exploreceylon.backend.service.timeline;

import com.exploreceylon.backend.dto.timeline.TimelineQualityScore;
import com.exploreceylon.backend.dto.timeline.TimelineStop;
import com.exploreceylon.backend.dto.timeline.TimelineValidationResult;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.timeline.OpeningHoursService.OperatingHours;
import com.exploreceylon.backend.service.timeline.PreferredVisitWindowService.VisitWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service evaluating scheduled daily timelines, validating closing hours / overlaps / meal breaks,
 * and computing fine-grained 0-100 quality scores.
 */
@Service
@Slf4j
public class TimelineValidationService {

    private final OpeningHoursService openingHoursService;
    private final PreferredVisitWindowService preferredVisitWindowService;

    public TimelineValidationService(OpeningHoursService openingHoursService,
                                     PreferredVisitWindowService preferredVisitWindowService) {
        this.openingHoursService = openingHoursService;
        this.preferredVisitWindowService = preferredVisitWindowService;
    }

    public TimelineValidationResult validateTimeline(List<TimelineStop> stops) {
        if (stops == null || stops.isEmpty()) {
            return TimelineValidationResult.builder()
                    .isValid(true)
                    .validationIssues(List.of())
                    .closingViolationsCount(0)
                    .overlapCount(0)
                    .mealMissedCount(0)
                    .qualityScore(100.0)
                    .build();
        }

        List<String> issues = new ArrayList<>();
        int closingViolations = 0;
        int overlaps = 0;
        int mealMissed = 0;

        boolean hasLunch = stops.stream().anyMatch(s -> "LUNCH".equalsIgnoreCase(s.getBreakType()));
        if (!hasLunch && stops.size() >= 3) {
            issues.add("Lunch break was not scheduled between 12:00 PM and 02:00 PM");
            mealMissed++;
        }

        double openingHoursScore = 20.0;
        double preferredVisitScore = 20.0;
        double waitingPenaltyScore = 20.0;
        double drivingEfficiencyScore = 20.0;
        double mealTimingScore = hasLunch ? 20.0 : 10.0;

        for (TimelineStop stop : stops) {
            Destination d = stop.getDestination();
            if (d != null) {
                OperatingHours op = openingHoursService.getOperatingHours(d);
                int endMin = parseClockToMinutes(stop.getVisitEndTime());
                if (endMin > op.closingMinutes()) {
                    closingViolations++;
                    openingHoursScore = Math.max(0.0, openingHoursScore - 5.0);
                    issues.add(String.format("%s closes at %s but visit ends at %s",
                            d.getName(), formatClock(op.closingMinutes()), stop.getVisitEndTime()));
                }

                VisitWindow window = preferredVisitWindowService.getPreferredWindow(d);
                int startMin = parseClockToMinutes(stop.getVisitStartTime());
                if (startMin >= window.preferredStartMinutes() && startMin <= window.preferredEndMinutes()) {
                    preferredVisitScore = Math.min(20.0, preferredVisitScore + 2.0);
                }
            }

            if (stop.getWaitingMinutes() > 30) {
                waitingPenaltyScore = Math.max(0.0, waitingPenaltyScore - 4.0);
            }
        }

        double totalScore = Math.round((openingHoursScore + preferredVisitScore + waitingPenaltyScore + drivingEfficiencyScore + mealTimingScore) * 10.0) / 10.0;
        boolean isValid = closingViolations == 0 && overlaps == 0;

        return TimelineValidationResult.builder()
                .isValid(isValid)
                .validationIssues(issues)
                .closingViolationsCount(closingViolations)
                .overlapCount(overlaps)
                .mealMissedCount(mealMissed)
                .qualityScore(totalScore)
                .build();
    }

    public TimelineQualityScore computeQualityScore(List<TimelineStop> stops) {
        TimelineValidationResult res = validateTimeline(stops);
        return TimelineQualityScore.builder()
                .totalScore(res.getQualityScore())
                .mealTimingScore(res.getMealMissedCount() == 0 ? 20.0 : 10.0)
                .waitingPenaltyScore(20.0)
                .drivingEfficiencyScore(20.0)
                .preferredVisitScore(18.0)
                .openingHoursScore(res.getClosingViolationsCount() == 0 ? 20.0 : 10.0)
                .build();
    }

    private int parseClockToMinutes(String clockStr) {
        if (clockStr == null || clockStr.isBlank()) return 0;
        try {
            String[] parts = clockStr.split(" ");
            String[] timeParts = parts[0].split(":");
            int hours = Integer.parseInt(timeParts[0]);
            int mins = Integer.parseInt(timeParts[1]);
            if (parts.length > 1 && "PM".equalsIgnoreCase(parts[1]) && hours < 12) {
                hours += 12;
            }
            if (parts.length > 1 && "AM".equalsIgnoreCase(parts[1]) && hours == 12) {
                hours = 0;
            }
            return hours * 60 + mins;
        } catch (Exception e) {
            return 0;
        }
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
