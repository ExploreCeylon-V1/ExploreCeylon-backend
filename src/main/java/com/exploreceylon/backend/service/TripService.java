package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.trip.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TripService {

    private final TripRepository       tripRepository;
    private final TripDayRepository    tripDayRepository;
    private final TripDayItemRepository tripDayItemRepository;
    private final UserRepository       userRepository;

    // ── Get current logged-in user ─────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── Create Trip ────────────────────────────────────────
    public TripResponse createTrip(CreateTripRequest req) {
        User user = getCurrentUser();
        log.info("Creating trip for user: {}", user.getEmail());

        Trip trip = Trip.builder()
                .user(user)
                .title(req.getTitle())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .travelStyle(req.getTravelStyle())
                .budgetRange(req.getBudgetRange())
                .groupSize(req.getGroupSize() != null ? req.getGroupSize() : 1)
                .aiGenerated(req.getGenerateWithAi() != null
                        && req.getGenerateWithAi())
                .build();

        // Save preference
        if (req.getRegions() != null || req.getInterests() != null) {
            TripPreference pref = TripPreference.builder()
                    .trip(trip)
                    .regions(req.getRegions() != null
                            ? String.join(",", req.getRegions()) : null)
                    .interests(req.getInterests() != null
                            ? String.join(",", req.getInterests()) : null)
                    .startingPoint(req.getStartingPoint())
                    .specialNotes(req.getSpecialNotes())
                    .build();
            trip.setPreference(pref);
        }

        // Auto-create empty day cards
        LocalDate current = req.getStartDate();
        int dayNum = 1;
        while (!current.isAfter(req.getEndDate())) {
            TripDay day = TripDay.builder()
                    .trip(trip)
                    .dayNumber(dayNum++)
                    .date(current)
                    .build();
            trip.getDays().add(day);
            current = current.plusDays(1);
        }

        Trip saved = tripRepository.save(trip);
        log.info("Trip created: id={}, days={}", saved.getId(),
                saved.getDays().size());
        return toResponse(saved);
    }

    // ── Get All Trips for current user ─────────────────────
    @Transactional(readOnly = true)
    public List<TripResponse> getMyTrips() {
        User user = getCurrentUser();
        return tripRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Trip By ID ─────────────────────────────────────
    @Transactional(readOnly = true)
    public TripResponse getTripById(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + id));
        return toResponse(trip);
    }

    // ── Get Trip By Share Token (public) ───────────────────
    @Transactional(readOnly = true)
    public TripResponse getTripByShareToken(String token) {
        Trip trip = tripRepository.findByShareToken(token)
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found for token: " + token));
        return toResponse(trip);
    }

    // ── Update Trip Day ────────────────────────────────────
    public TripDayResponse updateTripDay(Long tripId, Long dayId,
                                         TripDayResponse req) {
        TripDay day = tripDayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException(
                        "Day not found: " + dayId));
        if (req.getRegion() != null) day.setRegion(req.getRegion());
        if (req.getTheme()  != null) day.setTheme(req.getTheme());
        if (req.getTips()   != null) day.setTips(req.getTips());
        if (req.getEstimatedDayCost() != null)
            day.setEstimatedDayCost(req.getEstimatedDayCost());
        return toDayResponse(tripDayRepository.save(day));
    }

    // ── Add Item to Day ────────────────────────────────────
    public TripDayItemResponse addItemToDay(Long dayId,
                                             TripDayItemRequest req) {
        TripDay day = tripDayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException(
                        "Day not found: " + dayId));

        TripDayItem item = TripDayItem.builder()
                .tripDay(day)
                .type(req.getType())
                .referenceId(req.getReferenceId())
                .title(req.getTitle())
                .cost(req.getCost() != null ? req.getCost() : 0.0)
                .currency(req.getCurrency() != null
                        ? req.getCurrency() : "USD")
                .notes(req.getNotes())
                .orderIndex(req.getOrderIndex() != null
                        ? req.getOrderIndex() : 0)
                .build();

        // Update day estimated cost
        day.setEstimatedDayCost(
                day.getEstimatedDayCost() + item.getCost());
        tripDayRepository.save(day);

        TripDayItem saved = tripDayItemRepository.save(item);
        log.info("Item added to day {}: {}", dayId, item.getTitle());
        return toItemResponse(saved);
    }

    // ── Remove Item from Day ───────────────────────────────
    public void removeItemFromDay(Long dayId, Long itemId) {
        TripDayItem item = tripDayItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException(
                        "Item not found: " + itemId));

        // Update day cost
        TripDay day = item.getTripDay();
        day.setEstimatedDayCost(
                Math.max(0, day.getEstimatedDayCost() - item.getCost()));
        tripDayRepository.save(day);

        tripDayItemRepository.deleteById(itemId);
    }

    // ── Update Trip Status ─────────────────────────────────
    public TripResponse updateTripStatus(Long id, String status) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + id));
        trip.setStatus(Trip.TripStatus.valueOf(status.toUpperCase()));
        return toResponse(tripRepository.save(trip));
    }

    // ── Delete Trip ────────────────────────────────────────
    public void deleteTrip(Long id) {
        tripRepository.deleteById(id);
        log.info("Trip deleted: {}", id);
    }

    // ── MAPPER: Trip → TripResponse ────────────────────────
    private TripResponse toResponse(Trip t) {
        TripResponse res = new TripResponse();
        res.setId(t.getId());
        res.setTitle(t.getTitle());
        res.setStartDate(t.getStartDate());
        res.setEndDate(t.getEndDate());
        res.setTravelStyle(t.getTravelStyle());
        res.setBudgetRange(t.getBudgetRange());
        res.setGroupSize(t.getGroupSize());
        res.setStatus(t.getStatus());
        res.setAiGenerated(t.getAiGenerated());
        res.setShareToken(t.getShareToken());
        res.setCreatedAt(t.getCreatedAt());

        if (t.getPreference() != null) {
            res.setRegions(t.getPreference().getRegions());
            res.setInterests(t.getPreference().getInterests());
            res.setStartingPoint(t.getPreference().getStartingPoint());
        }

        res.setDays(t.getDays().stream()
                .map(this::toDayResponse)
                .collect(Collectors.toList()));
        return res;
    }

    // ── MAPPER: TripDay → TripDayResponse ─────────────────
    private TripDayResponse toDayResponse(TripDay d) {
        TripDayResponse res = new TripDayResponse();
        res.setId(d.getId());
        res.setDayNumber(d.getDayNumber());
        res.setDate(d.getDate());
        res.setRegion(d.getRegion());
        res.setTheme(d.getTheme());
        res.setTips(d.getTips());
        res.setFestivalEventId(d.getFestivalEventId());
        res.setEstimatedDayCost(d.getEstimatedDayCost());
        res.setItems(d.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList()));
        return res;
    }

    // ── MAPPER: TripDayItem → Response ────────────────────
    private TripDayItemResponse toItemResponse(TripDayItem i) {
        TripDayItemResponse res = new TripDayItemResponse();
        res.setId(i.getId());
        res.setType(i.getType());
        res.setReferenceId(i.getReferenceId());
        res.setTitle(i.getTitle());
        res.setCost(i.getCost());
        res.setCurrency(i.getCurrency());
        res.setBooked(i.getBooked());
        res.setOrderIndex(i.getOrderIndex());
        res.setNotes(i.getNotes());
        return res;
    }
}