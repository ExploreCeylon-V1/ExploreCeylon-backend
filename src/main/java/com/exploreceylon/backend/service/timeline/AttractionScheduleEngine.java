package com.exploreceylon.backend.service.timeline;

import com.exploreceylon.backend.dto.timeline.TimelineContext;
import com.exploreceylon.backend.dto.timeline.TimelineStop;

import java.util.List;

/**
 * Strategy interface for intelligently scheduling attraction visits into a clock-synchronized daily timeline.
 */
public interface AttractionScheduleEngine {

    /**
     * Schedules destinations into a realistic daily timeline with opening hours, waiting logic,
     * meal breaks, and driving fatigue rest periods.
     *
     * @param context TimelineContext containing trip day, pre-computed RouteMatrix, and start parameters.
     * @return List of TimelineStop DTOs.
     */
    List<TimelineStop> scheduleDayTimeline(TimelineContext context);
}
