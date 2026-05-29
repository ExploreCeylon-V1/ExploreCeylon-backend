package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.trip.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exploreceylon.backend.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    private final AiService            aiService;
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

    // ── Generate AI Itinerary ──────────────────────────────
    public TripResponse generateAiItinerary(GenerateAiTripRequest req) {
    log.info("Generating AI itinerary for trip: {}", req.getTripId());

    // Get trip
    Trip trip = tripRepository.findById(req.getTripId())
            .orElseThrow(() -> new RuntimeException(
                    "Trip not found: " + req.getTripId()));

    // Build region + interest lists
    List<String> regions = req.getRegions() != null
            ? req.getRegions() : List.of();
    List<String> interests = req.getInterests() != null
            ? req.getInterests() : List.of();

    // Travel style + budget
    String travelStyle = req.getTravelStyle() != null
            ? req.getTravelStyle().name() : "CULTURAL";
    String budgetRange = req.getBudgetRange() != null
            ? req.getBudgetRange().name() : "MID_RANGE";

    // Call Python AI Service
    JsonNode aiResponse = aiService.generateItinerary(
            req.getStartDate().toString(),
            req.getEndDate().toString(),
            travelStyle,
            budgetRange,
            req.getGroupSize(),
            regions,
            interests,
            req.getStartingPoint() != null
                    ? req.getStartingPoint() : "Colombo",
            req.getSpecialNotes()
    ).block(); // blocking call — sync

    if (aiResponse == null) {
        throw new RuntimeException("AI service returned null response");
    }

    // Check success
    if (!aiResponse.path("success").asBoolean(false)) {
        throw new RuntimeException("AI generation failed");
    }

    JsonNode data = aiResponse.path("data");

    // Update trip title if AI generated one
    String aiTitle = data.path("tripTitle").asText(null);
    if (aiTitle != null && !aiTitle.isEmpty()) {
        trip.setTitle(aiTitle);
    }
    trip.setAiGenerated(true);

    // Update preferences
    if (trip.getPreference() != null) {
        trip.getPreference().setRegions(
                String.join(",", regions));
        trip.getPreference().setInterests(
                String.join(",", interests));
        trip.getPreference().setStartingPoint(
                req.getStartingPoint());
        trip.getPreference().setSpecialNotes(
                req.getSpecialNotes());
    }

    // Clear existing days
    trip.getDays().clear();
    tripRepository.save(trip);

    // Build new days from AI response
    JsonNode daysNode = data.path("days");
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    if (daysNode.isArray()) {
        for (JsonNode dayNode : daysNode) {
            int dayNumber = dayNode.path("dayNumber").asInt();

            // Parse date
            LocalDate dayDate;
            try {
                dayDate = LocalDate.parse(
                        dayNode.path("date").asText(), fmt);
            } catch (Exception e) {
                dayDate = req.getStartDate().plusDays(dayNumber - 1);
            }

            TripDay day = TripDay.builder()
                    .trip(trip)
                    .dayNumber(dayNumber)
                    .date(dayDate)
                    .region(dayNode.path("region").asText(null))
                    .theme(dayNode.path("theme").asText(null))
                    .tips(dayNode.path("tips").asText(null))
                    .estimatedDayCost(
                            dayNode.path("estimatedDayCost").asDouble(0))
                    .build();

            // Add locations as GEM items
            JsonNode locations = dayNode.path("locations");
            int orderIndex = 0;
            if (locations.isArray()) {
                for (JsonNode loc : locations) {
                    TripDayItem item = TripDayItem.builder()
                            .tripDay(day)
                            .type(TripDayItem.ItemType.ACTIVITY)
                            .title(loc.asText())
                            .cost(0.0)
                            .currency("USD")
                            .orderIndex(orderIndex++)
                            .build();
                    day.getItems().add(item);
                }
            }

            // Add hidden gem if present
            String hiddenGem = dayNode.path("hiddenGem").asText(null);
            if (hiddenGem != null && !hiddenGem.equals("null")
                    && !hiddenGem.isEmpty()) {
                TripDayItem gemItem = TripDayItem.builder()
                        .tripDay(day)
                        .type(TripDayItem.ItemType.GEM)
                        .title("Hidden Gem: " + hiddenGem)
                        .cost(0.0)
                        .currency("USD")
                        .orderIndex(orderIndex++)
                        .build();
                day.getItems().add(gemItem);
            }

            // Add festival event if present
            String festivalEvent = dayNode.path("festivalEvent")
                    .asText(null);
            if (festivalEvent != null && !festivalEvent.equals("null")
                    && !festivalEvent.isEmpty()) {
                TripDayItem festItem = TripDayItem.builder()
                        .tripDay(day)
                        .type(TripDayItem.ItemType.ACTIVITY)
                        .title("Festival: " + festivalEvent)
                        .cost(0.0)
                        .currency("USD")
                        .orderIndex(orderIndex++)
                        .notes("Special festival event on this day!")
                        .build();
                day.getItems().add(festItem);
            }

            // Add transport suggestion
            String transport = dayNode.path("transport").asText(null);
            if (transport != null && !transport.isEmpty()) {
                TripDayItem transportItem = TripDayItem.builder()
                        .tripDay(day)
                        .type(TripDayItem.ItemType.TRANSPORT)
                        .title("Transport: " + transport)
                        .cost(0.0)
                        .currency("USD")
                        .orderIndex(orderIndex++)
                        .build();
                day.getItems().add(transportItem);
            }

            trip.getDays().add(day);
        }
    }

    Trip saved = tripRepository.save(trip);
    log.info("AI itinerary generated: {} days for trip {}",
            saved.getDays().size(), req.getTripId());
    return toResponse(saved);
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