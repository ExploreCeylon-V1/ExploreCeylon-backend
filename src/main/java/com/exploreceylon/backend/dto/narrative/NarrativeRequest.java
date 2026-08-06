package com.exploreceylon.backend.dto.narrative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO sent to the AI Service for narrative generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NarrativeRequest {
    private String tripTitle;
    private String origin;
    private String destination;
    private int durationDays;
    private String travelStyle;
    private String budgetRange;
    private int groupSize;
    private List<DaySummaryInfo> days;
    private List<String> hiddenGemNames;
    private List<String> eventNames;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaySummaryInfo {
        private int dayNumber;
        private List<String> stopNames;
        private List<String> arrivalTimes;
    }
}
