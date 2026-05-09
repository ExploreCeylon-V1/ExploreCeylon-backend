package com.exploreceylon.backend.dto.trip;

import com.exploreceylon.backend.model.TripDayItem;
import lombok.Data;

@Data
public class TripDayItemRequest {
    private TripDayItem.ItemType type;
    private String referenceId;
    private String title;
    private Double cost;
    private String currency;
    private String notes;
    private Integer orderIndex;
}