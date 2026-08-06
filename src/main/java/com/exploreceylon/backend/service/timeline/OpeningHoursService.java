package com.exploreceylon.backend.service.timeline;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service evaluating attraction opening and closing hours.
 * Uses category-based default windows if explicit database values are absent.
 */
@Service
@Slf4j
public class OpeningHoursService {

    public record OperatingHours(int openingMinutes, int closingMinutes) {}

    public OperatingHours getOperatingHours(Destination destination) {
        if (destination == null || destination.getCategory() == null) {
            return new OperatingHours(480, 1080); // 08:00 AM - 06:00 PM default
        }

        DestinationCategory category = destination.getCategory();
        return switch (category) {
            case RELIGIOUS -> new OperatingHours(360, 1140);        // 06:00 AM - 07:00 PM
            case CULTURAL, HERITAGE -> new OperatingHours(510, 1050); // 08:30 AM - 05:30 PM
            case WILDLIFE -> new OperatingHours(360, 1080);        // 06:00 AM - 06:00 PM
            case HILL -> new OperatingHours(360, 1110);            // 06:00 AM - 06:30 PM
            case BEACH, SURF -> new OperatingHours(360, 1140);     // 06:00 AM - 07:00 PM
            case ADVENTURE -> new OperatingHours(480, 1020);       // 08:00 AM - 05:00 PM
            case CITY -> new OperatingHours(540, 1200);            // 09:00 AM - 08:00 PM
            default -> new OperatingHours(480, 1080);              // 08:00 AM - 06:00 PM
        };
    }
}
