package com.exploreceylon.backend.service.timeline;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Service providing preferred visiting windows by destination category and tags (e.g. Sunrise, Sunset, Morning Temples).
 */
@Service
@Slf4j
public class PreferredVisitWindowService {

    public record VisitWindow(int preferredStartMinutes, int preferredEndMinutes, String windowName) {}

    public VisitWindow getPreferredWindow(Destination destination) {
        if (destination == null) {
            return new VisitWindow(480, 1080, "ALL_DAY");
        }

        String name = destination.getName() != null ? destination.getName().toLowerCase(Locale.ROOT) : "";
        DestinationCategory category = destination.getCategory();

        if (name.contains("sunrise") || name.contains("peak") || name.contains("adam")) {
            return new VisitWindow(360, 540, "SUNRISE"); // 06:00 AM - 09:00 AM
        } else if (name.contains("sunset") || name.contains("fort") || name.contains("rock")) {
            return new VisitWindow(960, 1110, "SUNSET"); // 04:00 PM - 06:30 PM
        }

        if (category == DestinationCategory.RELIGIOUS) {
            return new VisitWindow(420, 660, "MORNING_TEMPLE"); // 07:00 AM - 11:00 AM
        } else if (category == DestinationCategory.BEACH || category == DestinationCategory.SURF) {
            return new VisitWindow(900, 1140, "LATE_AFTERNOON"); // 03:00 PM - 07:00 PM
        } else if (category == DestinationCategory.WILDLIFE) {
            return new VisitWindow(360, 600, "MORNING_SAFARI"); // 06:00 AM - 10:00 AM
        }

        return new VisitWindow(540, 1020, "FLEXIBLE_DAY"); // 09:00 AM - 05:00 PM
    }
}
