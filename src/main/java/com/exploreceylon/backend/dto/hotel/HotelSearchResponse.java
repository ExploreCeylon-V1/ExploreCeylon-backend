package com.exploreceylon.backend.dto.hotel;

import lombok.Data;
import java.util.List;

@Data
public class HotelSearchResponse {
    private List<HotelResult> hotels;
    private int totalCount;
}
