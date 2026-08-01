package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.hotel.HotelBookingRequest;
import com.exploreceylon.backend.dto.hotel.HotelBookingResponse;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelBookingService {

    private final HotelBookingRepository hotelBookingRepository;
    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final TripDayItemRepository tripDayItemRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetItemRepository budgetItemRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─────────────────────────────────────────────────────────
    // BOOK A HOTEL
    // Called when user clicks "Book" on a hotel card
    // ─────────────────────────────────────────────────────────
    @Transactional
    public HotelBookingResponse bookHotel(HotelBookingRequest request) {

        // 1. Load trip (throws if not found)
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found: " + request.getTripId()));

        // 2. Load trip day (nullable)
        TripDay tripDay = null;
        if (request.getTripDayId() != null) {
            tripDay = tripDayRepository.findById(request.getTripDayId())
                    .orElse(null);
        }

        LocalDate checkIn  = LocalDate.parse(request.getCheckIn(),  DATE_FMT);
        LocalDate checkOut = LocalDate.parse(request.getCheckOut(), DATE_FMT);
        long nights = checkIn.until(checkOut).getDays();

        // Auto-calculate totalCost if not sent by frontend
        double totalCost = request.getTotalCost() != null
                ? request.getTotalCost()
                : request.getPricePerNight() * nights;

        // 3. Save HotelBooking entity
        HotelBooking booking = HotelBooking.builder()
                .trip(trip)
                .tripDay(tripDay)
                .hotelApiId(request.getHotelApiId())
                .hotelName(request.getHotelName())
                .hotelAddress(request.getHotelAddress())
                .photoUrl(request.getPhotoUrl())
                .stars(request.getStars() != null ? request.getStars() : 0)
                .reviewScore(request.getReviewScore() != null ? request.getReviewScore() : 0.0)
                .reviewScoreWord(request.getReviewScoreWord())
                .checkIn(checkIn)
                .checkOut(checkOut)
                .adults(request.getAdults() != null ? request.getAdults() : 1)
                .rooms(request.getRooms() != null ? request.getRooms() : 1)
                .pricePerNight(request.getPricePerNight())
                .totalCost(totalCost)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .status(HotelBooking.BookingStatus.CONFIRMED)
                .build();

        booking = hotelBookingRepository.save(booking);
        log.info("HotelBooking saved — id={}, hotel={}", booking.getId(), booking.getHotelName());

        // 4. Add to TripDayItem (if tripDay is set)
        //    This is what shows up on the Trip Planner day card
        if (tripDay != null) {
            addToTripDayItem(booking, tripDay);
        }

        // 5. Auto-add to BudgetItem
        //    This is what shows up in Budget Tracker
        addToBudget(booking, trip);

        return HotelBookingResponse.from(booking);
    }

    // ─────────────────────────────────────────────────────────
    // CANCEL A BOOKING
    // ─────────────────────────────────────────────────────────
    @Transactional
    public void cancelBooking(Long bookingId, Long tripId) {

        HotelBooking booking = hotelBookingRepository
                .findByIdAndTripId(bookingId, tripId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        booking.setStatus(HotelBooking.BookingStatus.CANCELLED);
        hotelBookingRepository.save(booking);

        // Remove TripDayItem so it disappears from the day card
        if (booking.getTripDay() != null) {
            tripDayItemRepository
                    .findByTripDayIdOrderByOrderIndex(booking.getTripDay().getId())
                    .stream()
                    .filter(item -> item.getType() == TripDayItem.ItemType.HOTEL
                            && booking.getId().toString().equals(item.getReferenceId()))
                    .forEach(tripDayItemRepository::delete);
        }

        // Remove BudgetItem so the cost disappears from Budget Tracker
        budgetItemRepository
                .findByBudgetIdOrderByCreatedAtDesc(
                        budgetRepository.findByTripId(tripId)
                                .map(Budget::getId)
                                .orElse(-1L))
                .stream()
                .filter(item -> booking.getId().toString().equals(item.getReferenceId()))
                .forEach(budgetItemRepository::delete);

        log.info("HotelBooking cancelled — id={}", bookingId);
    }

    // ─────────────────────────────────────────────────────────
    // GET all bookings for a trip
    // ─────────────────────────────────────────────────────────
    public List<HotelBookingResponse> getBookingsForTrip(Long tripId) {
        return hotelBookingRepository
                .findByTripIdOrderByCheckIn(tripId)
                .stream()
                .map(HotelBookingResponse::from)
                .toList();
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private void addToTripDayItem(HotelBooking booking, TripDay tripDay) {
        // Avoid duplicate if re-booking same hotel on same day
        boolean alreadyExists = tripDayItemRepository
                .findByTripDayIdOrderByOrderIndex(tripDay.getId())
                .stream()
                .anyMatch(item -> item.getType() == TripDayItem.ItemType.HOTEL
                        && booking.getId().toString().equals(item.getReferenceId()));

        if (alreadyExists) {
            log.warn("TripDayItem for booking {} already exists — skipping", booking.getId());
            return;
        }

        TripDayItem item = TripDayItem.builder()
                .tripDay(tripDay)
                .type(TripDayItem.ItemType.HOTEL)
                .referenceId(booking.getId().toString())
                .title(booking.getHotelName())
                .cost(booking.getTotalCost())
                .currency(booking.getCurrency())
                .booked(true)
                .notes("Check-in: " + booking.getCheckIn()
                        + " | Check-out: " + booking.getCheckOut())
                .build();

        tripDayItemRepository.save(item);
        log.info("TripDayItem added for hotel booking id={}", booking.getId());
    }

    private void addToBudget(HotelBooking booking, Trip trip) {
        budgetRepository.findByTripId(trip.getId()).ifPresent(budget -> {

            // Prevent duplicate auto-add using referenceId
            String refId = "hotel_booking_" + booking.getId();
            if (budgetItemRepository.existsByBudgetIdAndReferenceId(budget.getId(), refId)) {
                log.warn("BudgetItem for refId {} already exists — skipping", refId);
                return;
            }

            long nights = booking.getCheckIn().until(booking.getCheckOut()).getDays();

            BudgetItem budgetItem = BudgetItem.builder()
                    .budget(budget)
                    .category(BudgetItem.ItemCategory.HOTEL)
                    .title(booking.getHotelName()
                            + " (" + nights + " night" + (nights > 1 ? "s" : "") + ")")
                    .amount(booking.getTotalCost())
                    .currency(booking.getCurrency())
                    .date(booking.getCheckIn())
                    .autoAdded(true)      // auto-added from booking, not manually entered
                    .referenceId(refId)   // used to find and delete if booking is cancelled
                    .build();

            budgetItemRepository.save(budgetItem);
            log.info("BudgetItem auto-added for hotel booking id={}", booking.getId());
        });
    }
}