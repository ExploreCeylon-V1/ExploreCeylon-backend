package com.exploreceylon.backend.dto.trip;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import com.exploreceylon.backend.model.Trip.TravelStyle;
import com.exploreceylon.backend.model.Trip.BudgetRange;

@Data
public class CreateTripRequest {

    // Optional — auto-generate karanawa nattam
    @Size(max = 200, message = "Title too long")
    private String title;

    // NEW fields — UI eka anuwa
    @Size(max = 200)
    private String fromLocation;     // "Heading from"

    @Size(max = 200)
    private String toLocation;       // "Where to?"

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private TravelStyle travelStyle;

    private List<TravelStyle> travelStyles;

    private BudgetRange budgetRange;

    private Integer groupSize;

    private Boolean generateWithAi;

    // Old fields — backward compat ga thiyanna
    private List<String> regions;

    private List<String> interests;

    private String startingPoint;

    private String specialNotes;
}
