package com.exploreceylon.backend.dto.guide;

import com.exploreceylon.backend.model.GuideBooking;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GuideBookingResponse {
    private Long id;
    private Long guideId;
    private String guideName;
    private String guideDistrict;
    private Long userId;
    private Long tripId;
    private LocalDate startDate;
    private LocalDate endDate;
    private GuideBooking.BookingStatus status;
    private Double totalCost;
    private Double advanceAmount;
    private Double balanceAmount;
    private String notes;
    private LocalDateTime createdAt;
}
