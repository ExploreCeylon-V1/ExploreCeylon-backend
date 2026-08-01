package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopListsResponse {
    private List<TopDestination> topDestinations;
    private List<TopProvider> topGuides;
    private List<TopProvider> topVehicles;

    @Data
    @Builder
    public static class TopDestination {
        private Long id;
        private String name;
        private Double rating;
        private Integer reviewCount;
    }

    @Data
    @Builder
    public static class TopProvider {
        private Long id;
        private String name;
        private Long bookingCount;
    }
}
