package com.exploreceylon.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkBookingStatusRequest {
    @NotEmpty
    private List<BookingRef> items;
    @NotBlank
    private String status;

    @Data
    public static class BookingRef {
        private String type; // VEHICLE | GUIDE
        private Long id;
    }
}
