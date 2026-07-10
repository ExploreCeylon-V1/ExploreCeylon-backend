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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final VehicleBookingRepository vehicleBookingRepository;
    private final GuideBookingRepository   guideBookingRepository;
    private final BudgetRepository         budgetRepository;
    private final ItineraryAssemblyService itineraryAssemblyService;

    // Fixed conversion used only to compare the assembled itinerary's
    // USD cost estimate against a user's LKR budget target (fix 4).
    // Not a live FX rate — good enough for a warning-level estimate.
    private static final double USD_TO_LKR_RATE = 300.0;

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

        Trip.TravelStyle primaryTravelStyle =
                req.getTravelStyles() != null && !req.getTravelStyles().isEmpty()
                        ? req.getTravelStyles().get(0)
                        : req.getTravelStyle();

        Trip trip = Trip.builder()
                .user(user)
                .title(title)
                .fromLocation(req.getFromLocation())
                .toLocation(req.getToLocation())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .travelStyle(primaryTravelStyle)
                .budgetRange(req.getBudgetRange())
                .groupSize(req.getGroupSize() != null ? req.getGroupSize() : 1)
                .budgetAmountLkr(req.getBudgetAmountLkr())
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

    // ── Ownership guard — every non-public trip operation must own the trip ──
    private void assertOwner(Trip trip, User user) {
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to access this trip");
        }
    }

    // ── Get Trip By ID ─────────────────────────────────────
    @Transactional(readOnly = true)
    public TripResponse getTripById(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + id));
        assertOwner(trip, getCurrentUser());
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
        assertOwner(day.getTrip(), getCurrentUser());
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
        assertOwner(day.getTrip(), getCurrentUser());

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
        assertOwner(item.getTripDay().getTrip(), getCurrentUser());

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
        assertOwner(trip, getCurrentUser());
        trip.setStatus(TripStatus.valueOf(status.toUpperCase()));
        return toResponse(tripRepository.save(trip));
    }

    // ── Delete Trip ────────────────────────────────────────
    public void deleteTrip(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + id));
        assertOwner(trip, getCurrentUser());

        // Bookings are kept as historical records — detach them from the
        // trip instead of deleting, so the FK doesn't block trip deletion.
        vehicleBookingRepository.findByTripIdOrderByPickupDate(id)
                .forEach(b -> {
                    b.setTrip(null);
                    vehicleBookingRepository.save(b);
                });
        guideBookingRepository.findByTripIdOrderByStartDate(id)
                .forEach(b -> {
                    b.setTrip(null);
                    guideBookingRepository.save(b);
                });

        // The budget (and its items, via cascade) belongs to the trip.
        budgetRepository.findByTripId(id)
                .ifPresent(budgetRepository::delete);

        tripRepository.deleteById(id);
        log.info("Trip deleted: {}", id);
    }

    // ── Generate AI Itinerary ──────────────────────────────
    public TripResponse generateAiItinerary(GenerateAiTripRequest req) {
        log.info("Generating AI itinerary for trip: {}", req.getTripId());

        Trip trip = tripRepository.findById(req.getTripId())
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + req.getTripId()));
        assertOwner(trip, getCurrentUser());

        List<String> regions   = req.getRegions()   != null
                ? req.getRegions()   : List.of();
        List<String> interests = req.getInterests() != null
                ? req.getInterests() : List.of();

        String travelStyle =
                req.getTravelStyles() != null && !req.getTravelStyles().isEmpty()
                        ? req.getTravelStyles().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(", "))
                        : (req.getTravelStyle() != null
                                ? req.getTravelStyle().name() : "CULTURAL");
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

        int expectedDays = (int) (ChronoUnit.DAYS.between(
                req.getStartDate(), req.getEndDate()) + 1);
        int groupSize = req.getGroupSize() != null ? req.getGroupSize()
                : (trip.getGroupSize() != null ? trip.getGroupSize() : 1);

        // ── Geocode origin/destination (reuses the existing name-search
        // that used to live in prompt_builder.py — see
        // ItineraryAssemblyService.geocode) so the corridor can be built
        // between where the traveler is actually coming FROM and going TO,
        // not just around the destination alone. ──────────────────────
        ItineraryAssemblyService.GeoPoint origin = itineraryAssemblyService
                .geocode(startingPoint)
                .orElseGet(() -> new ItineraryAssemblyService.GeoPoint(6.9271, 79.8612)); // Colombo fallback
        String destinationQuery = (trip.getToLocation() != null
                && !trip.getToLocation().isBlank())
                ? trip.getToLocation()
                : (!regions.isEmpty() ? regions.get(0) : null);
        ItineraryAssemblyService.GeoPoint destinationPoint = destinationQuery != null
                ? itineraryAssemblyService.geocode(destinationQuery).orElse(origin)
                : origin;

        BudgetLevel budgetLevel;
        try {
            budgetLevel = BudgetLevel.valueOf(budgetRange);
        } catch (IllegalArgumentException e) {
            budgetLevel = BudgetLevel.MID_RANGE;
        }
        List<String> travelStylesList =
                req.getTravelStyles() != null && !req.getTravelStyles().isEmpty()
                        ? req.getTravelStyles().stream().map(Enum::name)
                                .collect(Collectors.toList())
                        : (req.getTravelStyle() != null
                                ? List.of(req.getTravelStyle().name()) : List.of());

        // ── Deterministic day/stop assembly — the backend, not the LLM,
        // now decides which stops go on which day (Phase 4). ───────────
        List<ItineraryAssemblyService.PlannedDay> plannedDays =
                itineraryAssemblyService.assemble(
                        origin, destinationPoint, req.getStartDate(),
                        expectedDays, groupSize, budgetLevel,
                        travelStylesList);

        if (plannedDays.stream().allMatch(d -> d.stops().isEmpty())) {
            throw new RuntimeException(
                    "Could not find any destinations/gems near this route to build an itinerary");
        }

        // ── Ask the AI service to write narrative only (title, per-day
        // theme/tips, per-stop description) for this fixed structure. A
        // failure here degrades gracefully to generic text instead of
        // failing the whole generation — the itinerary structure itself
        // is already valid without the LLM's involvement. ──────────────
        JsonNode narrativeData = null;
        try {
            JsonNode narrativeResp = aiService.generateNarrative(
                    buildNarrativeRequestBody(req, travelStyle, budgetRange,
                            startingPoint, trip.getToLocation(), groupSize, plannedDays)
            ).block();
            if (narrativeResp != null
                    && narrativeResp.path("success").asBoolean(false)) {
                narrativeData = narrativeResp.path("data");
            }
        } catch (Exception e) {
            log.warn("AI narrative generation failed, using generic day text: {}",
                    e.getMessage());
        }

        // Update title with AI's title, but only if the user hasn't
        // already customized it (renamed away from the auto-generated
        // placeholder) since the trip was created.
        String autoTitle = generateTripTitle(
                trip.getFromLocation(), trip.getToLocation(),
                trip.getStartDate(), trip.getEndDate());
        String aiTitle = narrativeData != null
                ? narrativeData.path("tripTitle").asText(null) : null;
        boolean titleIsCustomized = trip.getTitle() != null
                && !trip.getTitle().equals(autoTitle);
        if (aiTitle != null && !aiTitle.isBlank() && !titleIsCustomized) {
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

        // Clear existing days then rebuild from the planned structure
        trip.getDays().clear();
        tripRepository.save(trip);

        for (ItineraryAssemblyService.PlannedDay pd : plannedDays) {
            JsonNode narrativeDay = findNarrativeDay(narrativeData, pd.dayNumber());
            String theme = textOrNull(narrativeDay, "theme");
            String tips  = textOrNull(narrativeDay, "tips");

            TripDay day = TripDay.builder()
                    .trip(trip)
                    .dayNumber(pd.dayNumber())
                    .date(pd.date())
                    .region(pd.region())
                    .theme(theme != null ? theme
                            : "Day " + pd.dayNumber() + ": " + pd.region())
                    .tips(tips)
                    .estimatedDayCost(pd.estimatedDayCost())
                    .build();

            List<ItineraryAssemblyService.PlannedStop> stops = pd.stops();
            JsonNode narrativeStops = narrativeDay != null
                    ? narrativeDay.path("stops") : null;
            for (int i = 0; i < stops.size(); i++) {
                ItineraryAssemblyService.PlannedStop stop = stops.get(i);
                String description = (narrativeStops != null && narrativeStops.isArray()
                        && i < narrativeStops.size())
                        ? textOrNull(narrativeStops.get(i), "description")
                        : null;

                TripDayItem.ItemType itemType =
                        stop.type() == ItineraryAssemblyService.StopType.GEM
                                ? TripDayItem.ItemType.GEM
                                : TripDayItem.ItemType.ACTIVITY;
                String titlePrefix = switch (stop.type()) {
                    case GEM   -> "Hidden Gem: ";
                    case EVENT -> "Festival: ";
                    case DESTINATION -> "";
                };

                StringBuilder notes = new StringBuilder(
                        "[" + capitalizeSlot(stop.slot()) + "]");
                if (description != null && !description.isBlank()) {
                    notes.append(" ").append(description);
                }
                if (stop.type() == ItineraryAssemblyService.StopType.EVENT) {
                    notes.append(" Special festival event on this day!");
                }

                TripDayItem item = TripDayItem.builder()
                        .tripDay(day)
                        .type(itemType)
                        .title(titlePrefix + stop.name())
                        .referenceId(stop.referenceId() != null
                                ? stop.referenceId().toString() : null)
                        .cost(stop.costUsd() != null ? stop.costUsd() : 0.0)
                        .currency("USD")
                        .orderIndex(i)
                        .notes(notes.toString())
                        .build();
                day.getItems().add(item);
            }
            trip.getDays().add(day);
        }

        // Per-day monsoon check — flags each day's ACTUAL final region and
        // date, unlike the pre-generation check the AI service already does
        // internally against just the trip's single top-level destination.
        Map<String, String> monsoonNoteByRegion = new HashMap<>();
        for (TripDay day : trip.getDays()) {
            String region = day.getRegion();
            if (region == null || region.isBlank()
                    || monsoonNoteByRegion.containsKey(region)) {
                continue;
            }
            String note = null;
            try {
                JsonNode monsoonResp = aiService.checkMonsoon(
                        List.of(region), day.getDate().toString(),
                        day.getDate().toString()).block();
                JsonNode monsoonData = monsoonResp != null
                        ? monsoonResp.path("data") : null;
                if (monsoonData != null
                        && monsoonData.path("has_warning").asBoolean(false)
                        && monsoonData.path("warnings").isArray()
                        && !monsoonData.path("warnings").isEmpty()) {
                    note = monsoonData.path("warnings").get(0)
                            .path("recommendation").asText(null);
                }
            } catch (Exception e) {
                log.warn("Monsoon check failed for region {}: {}",
                        region, e.getMessage());
            }
            monsoonNoteByRegion.put(region, note);
        }
        for (TripDay day : trip.getDays()) {
            String note = monsoonNoteByRegion.get(day.getRegion());
            if (note != null && !note.isBlank()) {
                String warningLine = "⚠️ Monsoon note: " + note;
                String existingTips = day.getTips();
                day.setTips(existingTips == null || existingTips.isBlank()
                        ? warningLine
                        : existingTips + " " + warningLine);
            }
        }

        // ── Budget validation (fix 4) — warning only, no automatic
        // destination substitution (out of scope, see NOTE in
        // ItineraryAssemblyService.computeDayCost). Compares the
        // itinerary's total estimated cost (USD, summed from each
        // PlannedDay's real estimatedDayCost) against the user's numeric
        // LKR budget target, if one was set. ─────────────────────────
        if (req.getBudgetAmountLkr() != null) {
            trip.setBudgetAmountLkr(req.getBudgetAmountLkr());
        }
        if (trip.getBudgetAmountLkr() != null && !trip.getDays().isEmpty()) {
            double totalUsd = plannedDays.stream()
                    .mapToDouble(ItineraryAssemblyService.PlannedDay::estimatedDayCost)
                    .sum();
            double totalLkr = totalUsd * USD_TO_LKR_RATE;
            double budgetLkr = trip.getBudgetAmountLkr();
            if (totalLkr > budgetLkr) {
                double overPercent = ((totalLkr - budgetLkr) / budgetLkr) * 100.0;
                String warningLine = String.format(
                        "⚠️ Budget note: estimated cost (LKR %.0f) exceeds your budget of LKR %.0f by %.0f%%",
                        totalLkr, budgetLkr, overPercent);
                TripDay firstDay = trip.getDays().stream()
                        .min(java.util.Comparator.comparingInt(TripDay::getDayNumber))
                        .orElse(null);
                if (firstDay != null) {
                    String existingTips = firstDay.getTips();
                    firstDay.setTips(existingTips == null || existingTips.isBlank()
                            ? warningLine
                            : existingTips + " " + warningLine);
                }
            }
        }

        // Per-day cost is already real (Phase 5): ItineraryAssemblyService
        // computed each PlannedDay's estimatedDayCost from the actual
        // chosen stops' entryFeeUsd + accommodation/food/transport
        // estimates when the day was assembled above — no flat rate-table
        // overwrite needed here anymore.

        Trip saved = tripRepository.save(trip);
        log.info("AI itinerary generated: {} days for trip {}",
                saved.getDays().size(), req.getTripId());
        return toResponse(saved);
    }

    // ── Narrative request body builder (Phase 6 contract) ──
    // Sends the already-final day/stop structure to the AI service and
    // asks it to only write narrative text for it — see AiService
    // .generateNarrative() and ExploreCeylon-ai-service's
    // prompt_builder.build_narrative_prompt().
    private Map<String, Object> buildNarrativeRequestBody(
            GenerateAiTripRequest req, String travelStyle, String budgetRange,
            String startingPoint, String toLocation, int groupSize,
            List<ItineraryAssemblyService.PlannedDay> plannedDays) {

        List<Map<String, Object>> daysPayload = new ArrayList<>();
        for (ItineraryAssemblyService.PlannedDay pd : plannedDays) {
            List<Map<String, Object>> stopsPayload = new ArrayList<>();
            for (ItineraryAssemblyService.PlannedStop stop : pd.stops()) {
                Map<String, Object> stopMap = new HashMap<>();
                stopMap.put("type", stop.type().name());
                stopMap.put("name", stop.name());
                stopMap.put("slot", stop.slot());
                stopsPayload.add(stopMap);
            }
            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("dayNumber", pd.dayNumber());
            dayMap.put("date", pd.date().toString());
            dayMap.put("region", pd.region());
            dayMap.put("stops", stopsPayload);
            daysPayload.add(dayMap);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("start_date",     req.getStartDate().toString());
        body.put("end_date",       req.getEndDate().toString());
        body.put("travel_style",   travelStyle);
        body.put("budget_range",   budgetRange);
        body.put("group_size",     groupSize);
        body.put("starting_point", startingPoint);
        body.put("to_location",    toLocation);
        if (req.getSpecialNotes() != null) body.put("special_notes", req.getSpecialNotes());
        body.put("days", daysPayload);
        return body;
    }

    // Finds the narrative day matching a planned day number, tolerating a
    // missing/malformed AI response entirely (narrativeData null) or a
    // response that dropped/reordered days — index-by-dayNumber instead
    // of assuming positional alignment.
    private JsonNode findNarrativeDay(JsonNode narrativeData, int dayNumber) {
        if (narrativeData == null) return null;
        JsonNode days = narrativeData.path("days");
        if (!days.isArray()) return null;
        for (JsonNode d : days) {
            if (d.path("dayNumber").asInt(-1) == dayNumber) return d;
        }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank() || value.equalsIgnoreCase("null"))
                ? null : value;
    }

    private String capitalizeSlot(String slot) {
        if (slot == null || slot.isBlank()) return "Stop";
        return slot.charAt(0) + slot.substring(1).toLowerCase();
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
        res.setBudgetAmountLkr(t.getBudgetAmountLkr());
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
