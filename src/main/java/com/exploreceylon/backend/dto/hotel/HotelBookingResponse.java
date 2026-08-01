package com.exploreceylon.backend.dto.hotel;

import com.exploreceylon.backend.model.HotelBooking;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class HotelBookingResponse {

    private Long id;
    private Long tripId;
    private Long tripDayId;

    private String hotelApiId;
    private String hotelName;
    private String hotelAddress;
    private String photoUrl;
    private Integer stars;
    private Double reviewScore;
    private String reviewScoreWord;

    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer adults;
    private Integer rooms;

    private Double totalCost;
    private Double pricePerNight;
    private String currency;

    private HotelBooking.BookingStatus status;
    private LocalDateTime createdAt;

    // Convenience: total nights
    private Long nights;

    // Static mapper from entity
    public static HotelBookingResponse from(HotelBooking booking) {
        return HotelBookingResponse.builder()
                .id(booking.getId())
                .tripId(booking.getTrip().getId())
                .tripDayId(booking.getTripDay() != null ? booking.getTripDay().getId() : null)
                .hotelApiId(booking.getHotelApiId())
                .hotelName(booking.getHotelName())
                .hotelAddress(booking.getHotelAddress())
                .photoUrl(booking.getPhotoUrl())
                .stars(booking.getStars())
                .reviewScore(booking.getReviewScore())
                .reviewScoreWord(booking.getReviewScoreWord())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .adults(booking.getAdults())
                .rooms(booking.getRooms())
                .totalCost(booking.getTotalCost())
                .pricePerNight(booking.getPricePerNight())
                .currency(booking.getCurrency())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .nights(booking.getCheckIn() != null && booking.getCheckOut() != null
                        ? booking.getCheckIn().until(booking.getCheckOut()).getDays()
                        : 0L)
                .build();
    }
}