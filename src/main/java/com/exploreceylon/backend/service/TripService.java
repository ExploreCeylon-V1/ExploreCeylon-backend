package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.trip.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TripService {

    private final TripRepository        tripRepository;
    private final TripDayRepository     tripDayRepository;
    private final TripDayItemRepository tripDayItemRepository;
    private final UserRepository        userRepository;
    private final AiService             aiService;

    // ── Get current logged-in user ─────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── Auto-generate trip title ───────────────────────────
    private String generateTripTitle(String fromLocation,
                                        String toLocation,
                                        LocalDate startDate,
                                        LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        String from = (fromLocation != null && !fromLocation.isBlank())
                ? fromLocation.trim() : null;
        String to   = (toLocation   != null && !toLocation.isBlank())
                ? toLocation.trim()   : null;

        // ✅ em dash වෙනුවට simple hyphen use කරන්න
        if (from == null && to == null)
                return days + " Days - Sri Lanka Adventure";
        if (from == null)
                return days + " Days - Explore " + to;
        if (to == null)
                return days + " Days - From " + from;
        if (from.equalsIgnoreCase(to))
                return days + " Days - Explore " + to;

        return days + " Days - " + from + " to " + to;
        }

    // ── Create Trip ────────────────────────────────────────
    public TripResponse createTrip(CreateTripRequest req) {
        User user = getCurrentUser();
        log.info("Creating trip for user: {}", user.getEmail());

        // Auto-generate title if not provided
        String title = (req.getTitle() != null && !req.getTitle().isBlank())
                ? req.getTitle()
                : generateTripTitle(
                        req.getFromLocation(),
                        req.getToLocation(),
                        req.getStartDate(),
                        req.getEndDate());

        Trip trip = Trip.builder()
                .user(user)
                .title(title)
                .fromLocation(req.getFromLocation())
                .toLocation(req.getToLocation())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .travelStyle(req.getTravelStyle())
                .budgetRange(req.getBudgetRange())
                .groupSize(req.getGroupSize() != null ? req.getGroupSize() : 1)
                .status(TripStatus.DRAFT)
                .aiGenerated(Boolean.TRUE.equals(req.getGenerateWithAi()))
                .build();

        // Save preference
        if (req.getFromLocation() != null || req.getToLocation() != null
                || req.getRegions() != null || req.getInterests() != null) {
            TripPreference pref = TripPreference.builder()
                    .trip(trip)
                    .regions(req.getRegions() != null
                            ? String.join(",", req.getRegions())
                            : req.getToLocation())
                    .interests(req.getInterests() != null
                            ? String.join(",", req.getInterests()) : null)
                    .startingPoint(req.getFromLocation() != null
                            ? req.getFromLocation()
                            : req.getStartingPoint())
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
        log.info("Trip created: id={}, title='{}', days={}",
                saved.getId(), saved.getTitle(), saved.getDays().size());
        return toResponse(saved);
    }

    // ── Update Trip Title ──────────────────────────────────
    public TripResponse updateTripTitle(Long tripId, String newTitle) {
        User user = getCurrentUser();

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + tripId));

        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to edit this trip");
        }

        if (newTitle == null || newTitle.isBlank()) {
            throw new RuntimeException("Title cannot be empty");
        }

        trip.setTitle(newTitle.trim());
        Trip saved = tripRepository.save(trip);
        log.info("Trip title updated: id={}, title='{}'",
                saved.getId(), saved.getTitle());
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
        if (req.getRegion()           != null) day.setRegion(req.getRegion());
        if (req.getTheme()            != null) day.setTheme(req.getTheme());
        if (req.getTips()             != null) day.setTips(req.getTips());
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
                .cost(req.getCost()     != null ? req.getCost()     : 0.0)
                .currency(req.getCurrency() != null ? req.getCurrency() : "USD")
                .notes(req.getNotes())
                .orderIndex(req.getOrderIndex() != null ? req.getOrderIndex() : 0)
                .build();

        // Update day estimated cost
        day.setEstimatedDayCost(day.getEstimatedDayCost() + item.getCost());
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
        trip.setStatus(TripStatus.valueOf(status.toUpperCase()));
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

        Trip trip = tripRepository.findById(req.getTripId())
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + req.getTripId()));

        List<String> regions   = req.getRegions()   != null
                ? req.getRegions()   : List.of();
        List<String> interests = req.getInterests() != null
                ? req.getInterests() : List.of();

        String travelStyle = req.getTravelStyle() != null
                ? req.getTravelStyle().name() : "CULTURAL";
        String budgetRange = req.getBudgetRange() != null
                ? req.getBudgetRange().name() : "MID_RANGE";

        // Determine starting point — prefer fromLocation
        String startingPoint = (trip.getFromLocation() != null
                && !trip.getFromLocation().isBlank())
                ? trip.getFromLocation()
                : (req.getStartingPoint() != null
                        ? req.getStartingPoint() : "Colombo");

        // If toLocation set and regions empty, use toLocation as region hint
        if (regions.isEmpty() && trip.getToLocation() != null
                && !trip.getToLocation().isBlank()) {
            regions = List.of(trip.getToLocation());
        }

        // Call Python AI Service
        JsonNode aiResponse = aiService.generateItinerary(
                req.getStartDate().toString(),
                req.getEndDate().toString(),
                travelStyle,
                budgetRange,
                req.getGroupSize(),
                regions,
                interests,
                startingPoint,
                req.getSpecialNotes()
        ).block();

        if (aiResponse == null) {
            throw new RuntimeException("AI service returned null response");
        }
        if (!aiResponse.path("success").asBoolean(false)) {
            throw new RuntimeException("AI generation failed");
        }

        JsonNode data = aiResponse.path("data");

        // Update title — prefer AI title, else keep auto-generated
        String aiTitle = data.path("tripTitle").asText(null);
        if (aiTitle != null && !aiTitle.isBlank()) {
            trip.setTitle(aiTitle);
        }
        trip.setAiGenerated(true);

        // Update preference
        if (trip.getPreference() != null) {
            trip.getPreference().setRegions(String.join(",", regions));
            trip.getPreference().setInterests(String.join(",", interests));
            trip.getPreference().setStartingPoint(startingPoint);
            trip.getPreference().setSpecialNotes(req.getSpecialNotes());
        }

        // Clear existing days then rebuild from AI response
        trip.getDays().clear();
        tripRepository.save(trip);

        JsonNode daysNode = data.path("days");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (daysNode.isArray()) {
            for (JsonNode dayNode : daysNode) {
                int dayNumber = dayNode.path("dayNumber").asInt();

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

                int orderIndex = 0;

                // Locations → ACTIVITY items
                JsonNode locations = dayNode.path("locations");
                if (locations.isArray()) {
                    for (JsonNode loc : locations) {
                        day.getItems().add(TripDayItem.builder()
                                .tripDay(day)
                                .type(TripDayItem.ItemType.ACTIVITY)
                                .title(loc.asText())
                                .cost(0.0).currency("USD")
                                .orderIndex(orderIndex++)
                                .build());
                    }
                }

                // Hidden gem → GEM item
                String hiddenGem = dayNode.path("hiddenGem").asText(null);
                if (hiddenGem != null && !hiddenGem.equals("null")
                        && !hiddenGem.isBlank()) {
                    day.getItems().add(TripDayItem.builder()
                            .tripDay(day)
                            .type(TripDayItem.ItemType.GEM)
                            .title("Hidden Gem: " + hiddenGem)
                            .cost(0.0).currency("USD")
                            .orderIndex(orderIndex++)
                            .build());
                }

                // Festival event → ACTIVITY item
                String festivalEvent =
                        dayNode.path("festivalEvent").asText(null);
                if (festivalEvent != null && !festivalEvent.equals("null")
                        && !festivalEvent.isBlank()) {
                    day.getItems().add(TripDayItem.builder()
                            .tripDay(day)
                            .type(TripDayItem.ItemType.ACTIVITY)
                            .title("Festival: " + festivalEvent)
                            .cost(0.0).currency("USD")
                            .orderIndex(orderIndex++)
                            .notes("Special festival event on this day!")
                            .build());
                }

                // Transport → TRANSPORT item
                String transport = dayNode.path("transport").asText(null);
                if (transport != null && !transport.isBlank()) {
                    day.getItems().add(TripDayItem.builder()
                            .tripDay(day)
                            .type(TripDayItem.ItemType.TRANSPORT)
                            .title("Transport: " + transport)
                            .cost(0.0).currency("USD")
                            .orderIndex(orderIndex++)
                            .build());
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
        res.setFromLocation(t.getFromLocation());
        res.setToLocation(t.getToLocation());
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
                .sorted(java.util.Comparator.comparingInt(TripDay::getDayNumber))
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
                .sorted(java.util.Comparator.comparingInt(TripDayItem::getOrderIndex))
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