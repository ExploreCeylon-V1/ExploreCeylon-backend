package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.hotel.HotelBookingRequest;
import com.exploreceylon.backend.dto.hotel.HotelBookingResponse;
import com.exploreceylon.backend.service.HotelBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels/bookings")
@RequiredArgsConstructor
public class HotelBookingController {

    private final HotelBookingService hotelBookingService;

    // POST /api/v1/hotels/bookings
    // Frontend calls this when user clicks "Book" on a hotel card
    @PostMapping
    public ResponseEntity<HotelBookingResponse> bookHotel(
            @RequestBody HotelBookingRequest request) {
        return ResponseEntity.ok(hotelBookingService.bookHotel(request));
    }

    // GET /api/v1/hotels/bookings/trip/{tripId}
    // Load all hotel bookings for a trip (trip planner page)
    @GetMapping("/trip/{tripId}")
    public ResponseEntity<List<HotelBookingResponse>> getBookingsForTrip(
            @PathVariable Long tripId) {
        return ResponseEntity.ok(hotelBookingService.getBookingsForTrip(tripId));
    }

    // DELETE /api/v1/hotels/bookings/{bookingId}?tripId=1
    // User removes a hotel from their trip
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long bookingId,
            @RequestParam Long tripId) {
        hotelBookingService.cancelBooking(bookingId, tripId);
        return ResponseEntity.noContent().build();
    }
}