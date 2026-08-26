package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.trip.*;
import com.exploreceylon.backend.exception.ForbiddenException;
import com.exploreceylon.backend.exception.ResourceNotFoundException;
import com.exploreceylon.backend.exception.UnauthenticatedException;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final HotelBookingRepository   hotelBookingRepository;
    private final DestinationRepository    destinationRepository;
    private final HiddenGemRepository      hiddenGemRepository;
    private final BudgetRepository         budgetRepository;
    private final BudgetItemRepository     budgetItemRepository;
    private final ItineraryAssemblyService itineraryAssemblyService;
    private final com.exploreceylon.backend.service.planner.PlannerFacadeService plannerFacadeService;
    private final com.exploreceylon.backend.service.planner.PlannerTripMapper plannerTripMapper;
    private final com.exploreceylon.backend.repository.PlannerMetadataRepository plannerMetadataRepository;
    private final com.exploreceylon.backend.repository.PlannerCostSnapshotRepository plannerCostSnapshotRepository;
    private final com.exploreceylon.backend.repository.TripActivityLogRepository activityLogRepository;

    // Fixed conversion used only to compare the assembled itinerary's
    // USD cost estimate against a user's LKR budget target.
    // Standard conversion rate aligned across system services (325.0 LKR / USD).
    private static final double USD_TO_LKR_RATE = 325.0;

    // ── Get current logged-in user ─────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthenticatedException("User not found"));
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
            throw new ForbiddenException("Not authorized to access this trip");
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip not found for token: " + token));
        TripResponse res = toResponse(trip);

        budgetRepository.findByTripId(trip.getId()).ifPresent(budget -> {
            res.setBudgetSummary(toPublicBudgetSummary(budget));
        });

        return res;
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

        // Clean up planner metadata, cost snapshots, and activity logs to prevent FK constraint errors
        plannerMetadataRepository.findByTripId(id)
                .ifPresent(plannerMetadataRepository::delete);
        plannerCostSnapshotRepository.findByTripId(id)
                .ifPresent(plannerCostSnapshotRepository::delete);
        activityLogRepository.findByTripIdOrderByCreatedAtDesc(id)
                .forEach(activityLogRepository::delete);

        // The budget (and its items, via cascade) belongs to the trip.
        budgetRepository.findByTripId(id)
                .ifPresent(budgetRepository::delete);

        tripRepository.deleteById(id);
        log.info("Trip deleted cleanly: {}", id);
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

        // ── Execute full 13-phase Planner Facade Pipeline ────────────
        com.exploreceylon.backend.dto.planner.PlannerRequest plannerRequest =
                com.exploreceylon.backend.dto.planner.PlannerRequest.builder()
                        .origin(startingPoint)
                        .destination(destinationQuery != null ? destinationQuery : startingPoint)
                        .tripDays(expectedDays)
                        .budget(budgetRange)
                        .travelStyle(travelStyle)
                        .groupSize(groupSize)
                        .startDate(req.getStartDate())
                        .preferences(interests)
                        .specialNotes(req.getSpecialNotes())
                        .build();

        com.exploreceylon.backend.dto.planner.PlannerResponse plannerResponse =
                plannerFacadeService.generateItinerary(plannerRequest);

        // Update preference
        if (trip.getPreference() != null) {
            trip.getPreference().setRegions(String.join(",", regions));
            trip.getPreference().setInterests(String.join(",", interests));
            trip.getPreference().setStartingPoint(startingPoint);
            trip.getPreference().setSpecialNotes(req.getSpecialNotes());
        }

        // Clear existing days then rebuild from the 13-phase planned structure
        trip.getDays().clear();
        tripRepository.save(trip);

        if (plannerResponse.getDays() != null) {
            for (ItineraryAssemblyService.PlannedDay pd : plannerResponse.getDays()) {
                TripDay day = TripDay.builder()
                        .trip(trip)
                        .dayNumber(pd.dayNumber())
                        .date(pd.date())
                        .region(pd.region())
                        .theme("Day " + pd.dayNumber() + ": " + pd.region())
                        .tips("Optimized via ExploreCeylon 13-Phase Pipeline")
                        .estimatedDayCost(pd.estimatedDayCost())
                        .items(new java.util.ArrayList<>())
                        .build();

                if (pd.stops() != null) {
                    int order = 1;
                    for (ItineraryAssemblyService.PlannedStop s : pd.stops()) {
                        TripDayItem.ItemType itemType =
                                s.type() == ItineraryAssemblyService.StopType.GEM
                                        ? TripDayItem.ItemType.GEM
                                        : TripDayItem.ItemType.ACTIVITY;

                        TripDayItem item = TripDayItem.builder()
                                .tripDay(day)
                                .type(itemType)
                                .title(s.name())
                                .referenceId(s.referenceId() != null ? s.referenceId().toString() : null)
                                .cost(s.costUsd() != null ? s.costUsd() : 0.0)
                                .currency("USD")
                                .orderIndex(order++)
                                .notes("[" + s.slot() + "] " + s.name())
                                .build();
                        day.getItems().add(item);
                    }
                }
                trip.getDays().add(day);
            }
        }

        trip.setAiGenerated(true);
        trip.setStatus(TripStatus.GENERATED);
        if (req.getBudgetRange() != null) {
            trip.setBudgetRange(req.getBudgetRange());
        }
        double newEstimatedTotal = trip.getDays().stream()
                .mapToDouble(d -> d.getEstimatedDayCost() != null ? d.getEstimatedDayCost() : 0.0)
                .sum();
        if (newEstimatedTotal > 0) {
            trip.setBudgetAmountLkr(newEstimatedTotal);
        }

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

    // ── MAPPER: Budget → TripBudgetSummaryResponse (public) ──
    private TripBudgetSummaryResponse toPublicBudgetSummary(Budget budget) {
        if (budget == null) return null;

        List<BudgetItem> items = budget.getItems() != null ? budget.getItems() : java.util.Collections.emptyList();

        double totalSpent = items.stream()
                .mapToDouble(BudgetItem::getAmount)
                .sum();
        double totalBudget = budget.getTotalBudget() != null ? budget.getTotalBudget() : 0.0;
        double remaining = totalBudget - totalSpent;
        double usedPercentage = totalBudget > 0
                ? (totalSpent / totalBudget) * 100.0
                : 0.0;

        String status = "ON_TRACK";
        if (usedPercentage > 100.0) {
            status = "OVER_BUDGET";
        } else if (usedPercentage >= 80.0) {
            status = "WARNING";
        }

        Map<String, Double> categoryBudgets = new HashMap<>();
        if (budget.getCategoryBudgets() != null) {
            budget.getCategoryBudgets().forEach((cat, amt) -> {
                if (cat != null) categoryBudgets.put(cat.name(), amt);
            });
        }

        Map<String, Double> categorySpent = new HashMap<>();
        items.forEach(item -> {
            if (item.getCategory() != null) {
                categorySpent.merge(item.getCategory().name(), item.getAmount(), Double::sum);
            }
        });

        List<TripBudgetSummaryResponse.PublicBudgetItemResponse> publicItems = items.stream()
                .map(item -> TripBudgetSummaryResponse.PublicBudgetItemResponse.builder()
                        .id(item.getId())
                        .category(item.getCategory() != null ? item.getCategory().name() : "OTHER")
                        .title(item.getTitle())
                        .amount(item.getAmount())
                        .currency(item.getCurrency() != null ? item.getCurrency() : "USD")
                        .date(item.getDate())
                        .autoAdded(item.getAutoAdded())
                        .notes(item.getNotes())
                        .build())
                .collect(Collectors.toList());

        return TripBudgetSummaryResponse.builder()
                .totalBudget(totalBudget)
                .totalSpent(totalSpent)
                .remaining(remaining)
                .usedPercentage(usedPercentage)
                .currency(budget.getCurrency() != null ? budget.getCurrency() : "USD")
                .status(status)
                .categoryBudgets(categoryBudgets)
                .categorySpent(categorySpent)
                .items(publicItems)
                .build();
    }

    // ── Add Day to Trip (Append Only) ──────────────────────
    public TripResponse addDayToTrip(Long tripId, AddDayRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        assertOwner(trip, getCurrentUser());

        List<TripDay> days = trip.getDays();
        TripDay lastDay = (days != null && !days.isEmpty())
                ? days.stream().max(Comparator.comparingInt(TripDay::getDayNumber)).orElse(days.get(days.size() - 1))
                : null;

        int newDayNumber = (lastDay != null) ? lastDay.getDayNumber() + 1 : 1;
        LocalDate newDate = (lastDay != null && lastDay.getDate() != null)
                ? lastDay.getDate().plusDays(1)
                : trip.getEndDate().plusDays(1);

        // Resolve leg origin coordinate
        ItineraryAssemblyService.GeoPoint legOrigin = null;
        if (lastDay != null && lastDay.getItems() != null && !lastDay.getItems().isEmpty()) {
            TripDayItem lastItem = lastDay.getItems().stream()
                    .max(Comparator.comparingInt(TripDayItem::getOrderIndex))
                    .orElse(lastDay.getItems().get(lastDay.getItems().size() - 1));
            if (lastItem.getReferenceId() != null) {
                try {
                    Long refId = Long.parseLong(lastItem.getReferenceId());
                    if (lastItem.getType() == TripDayItem.ItemType.GEM) {
                        legOrigin = hiddenGemRepository.findById(refId)
                                .filter(g -> g.getLatitude() != null && g.getLongitude() != null)
                                .map(g -> new ItineraryAssemblyService.GeoPoint(g.getLatitude(), g.getLongitude()))
                                .orElse(null);
                    } else {
                        legOrigin = destinationRepository.findById(refId)
                                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                                .map(d -> new ItineraryAssemblyService.GeoPoint(d.getLatitude(), d.getLongitude()))
                                .orElse(null);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (legOrigin == null && lastDay != null && lastDay.getRegion() != null) {
            legOrigin = itineraryAssemblyService.geocode(lastDay.getRegion()).orElse(null);
        }
        if (legOrigin == null) {
            legOrigin = itineraryAssemblyService.geocode(trip.getToLocation())
                    .or(() -> itineraryAssemblyService.geocode(trip.getFromLocation()))
                    .orElse(new ItineraryAssemblyService.GeoPoint(6.9271, 79.8612));
        }

        String targetArea = req.getTargetArea().trim();
        ItineraryAssemblyService.GeoPoint targetGeo = itineraryAssemblyService.geocode(targetArea).orElse(legOrigin);

        BudgetLevel budgetLevel = BudgetLevel.MID_RANGE;
        if (trip.getBudgetRange() != null) {
            try {
                budgetLevel = BudgetLevel.valueOf(trip.getBudgetRange().name());
            } catch (Exception ignored) {}
        }
        int groupSize = trip.getGroupSize() != null ? trip.getGroupSize() : 2;
        List<String> travelStyles = req.getTravelStyles() != null && !req.getTravelStyles().isEmpty()
                ? req.getTravelStyles()
                : (trip.getTravelStyle() != null ? List.of(trip.getTravelStyle().name()) : List.of("BALANCED"));

        List<ItineraryAssemblyService.PlannedDay> plannedDays = itineraryAssemblyService.assemble(
                legOrigin, targetGeo, newDate, 1, groupSize, budgetLevel, travelStyles, null);

        ItineraryAssemblyService.PlannedDay pd = (plannedDays != null && !plannedDays.isEmpty())
                ? plannedDays.get(0)
                : new ItineraryAssemblyService.PlannedDay(1, newDate, targetArea, List.of(), 0.0);

        String dayRegion = (pd.region() != null && !pd.region().isBlank()) ? pd.region() : targetArea;

        // Generate narrative for ONLY this new day
        List<Map<String, Object>> stopsPayload = new ArrayList<>();
        if (pd.stops() != null) {
            for (ItineraryAssemblyService.PlannedStop s : pd.stops()) {
                Map<String, Object> stopMap = new HashMap<>();
                stopMap.put("type", s.type().name());
                stopMap.put("name", s.name());
                stopMap.put("slot", s.slot());
                stopsPayload.add(stopMap);
            }
        }
        Map<String, Object> dayMap = new HashMap<>();
        dayMap.put("dayNumber", newDayNumber);
        dayMap.put("date", newDate.toString());
        dayMap.put("region", dayRegion);
        dayMap.put("stops", stopsPayload);

        Map<String, Object> narrativeBody = new HashMap<>();
        narrativeBody.put("start_date", newDate.toString());
        narrativeBody.put("end_date", newDate.toString());
        narrativeBody.put("travel_style", String.join(", ", travelStyles));
        narrativeBody.put("budget_range", budgetLevel.name());
        narrativeBody.put("group_size", groupSize);
        narrativeBody.put("starting_point", lastDay != null && lastDay.getRegion() != null ? lastDay.getRegion() : trip.getFromLocation());
        narrativeBody.put("to_location", targetArea);
        narrativeBody.put("days", List.of(dayMap));

        String dayTheme = "Day " + newDayNumber + ": " + dayRegion;
        String dayTips = "Explore the highlights of " + dayRegion + ". Stay hydrated and keep local currency on hand.";
        Map<Integer, String> stopDescriptions = new HashMap<>();

        try {
            JsonNode narrativeData = aiService.generateNarrative(narrativeBody)
                    .block(java.time.Duration.ofSeconds(10));
            if (narrativeData != null) {
                JsonNode aiData = narrativeData.path("data");
                JsonNode narrativeDay = findNarrativeDay(aiData.isMissingNode() ? narrativeData : aiData, newDayNumber);
                if (narrativeDay != null) {
                    String aiTheme = textOrNull(narrativeDay, "theme");
                    String aiTips = textOrNull(narrativeDay, "tips");
                    if (aiTheme != null) dayTheme = aiTheme;
                    if (aiTips != null) dayTips = aiTips;

                    JsonNode aiStops = narrativeDay.path("stops");
                    if (aiStops.isArray()) {
                        for (int i = 0; i < aiStops.size(); i++) {
                            String desc = textOrNull(aiStops.get(i), "description");
                            if (desc != null) stopDescriptions.put(i, desc);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI narrative generation failed for added day {}. Using fallback.", newDayNumber, e);
        }

        TripDay newDay = TripDay.builder()
                .trip(trip)
                .dayNumber(newDayNumber)
                .date(newDate)
                .region(dayRegion)
                .theme(dayTheme)
                .tips(dayTips)
                .estimatedDayCost(pd.estimatedDayCost())
                .items(new ArrayList<>())
                .build();

        if (pd.stops() != null) {
            int order = 1;
            int stopIdx = 0;
            for (ItineraryAssemblyService.PlannedStop s : pd.stops()) {
                TripDayItem.ItemType itemType = (s.type() == ItineraryAssemblyService.StopType.GEM)
                        ? TripDayItem.ItemType.GEM
                        : TripDayItem.ItemType.ACTIVITY;
                String desc = stopDescriptions.get(stopIdx++);
                String itemNotes = desc != null ? "[" + s.slot() + "] " + desc : "[" + s.slot() + "] " + s.name();

                TripDayItem item = TripDayItem.builder()
                        .tripDay(newDay)
                        .type(itemType)
                        .title(s.name())
                        .referenceId(s.referenceId() != null ? String.valueOf(s.referenceId()) : null)
                        .cost(s.costUsd() != null ? s.costUsd() : 0.0)
                        .currency("USD")
                        .orderIndex(order++)
                        .notes(itemNotes)
                        .build();
                newDay.getItems().add(item);
            }
        }

        trip.getDays().add(newDay);
        tripDayRepository.save(newDay);

        trip.setEndDate(newDate);
        double dayCost = pd.estimatedDayCost();
        double dayCostLkr = dayCost * USD_TO_LKR_RATE;
        trip.setBudgetAmountLkr((trip.getBudgetAmountLkr() != null ? trip.getBudgetAmountLkr() : 0.0) + dayCostLkr);
        Trip savedTrip = tripRepository.save(trip);

        Optional<Budget> budgetOpt = budgetRepository.findByTripId(tripId);
        if (budgetOpt.isPresent()) {
            Budget budget = budgetOpt.get();
            budget.setTotalBudget((budget.getTotalBudget() != null ? budget.getTotalBudget() : 0.0) + dayCost);
            if (budget.getCategoryBudgets() != null && !budget.getCategoryBudgets().isEmpty()) {
                BudgetItem.ItemCategory targetCategory = budget.getCategoryBudgets().containsKey(BudgetItem.ItemCategory.MISC)
                        ? BudgetItem.ItemCategory.MISC
                        : (budget.getCategoryBudgets().containsKey(BudgetItem.ItemCategory.ACTIVITY)
                            ? BudgetItem.ItemCategory.ACTIVITY
                            : budget.getCategoryBudgets().keySet().iterator().next());
                double currentVal = budget.getCategoryBudgets().getOrDefault(targetCategory, 0.0);
                budget.getCategoryBudgets().put(targetCategory, currentVal + dayCost);
            }
            budgetRepository.save(budget);
        }

        log.info("Appended Day {} to trip {}: date={}, region={}, estimatedCost={}",
                newDayNumber, tripId, newDate, dayRegion, dayCost);
        return toResponse(savedTrip);
    }

    // ── Remove Last Day from Trip ─────────────────────────
    public TripResponse removeLastDay(Long tripId, Long dayId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        assertOwner(trip, getCurrentUser());

        List<TripDay> days = trip.getDays();
        if (days == null || days.size() <= 1) {
            throw new RuntimeException("Cannot remove day: Trip must have at least 1 day.");
        }

        TripDay lastDay = days.stream()
                .max(Comparator.comparingInt(TripDay::getDayNumber))
                .orElse(days.get(days.size() - 1));

        if (!lastDay.getId().equals(dayId)) {
            throw new RuntimeException("Only the last day of the trip (Day " + lastDay.getDayNumber() + ") can be removed.");
        }

        LocalDate targetDate = lastDay.getDate();

        // 1. Vehicle booking guard
        boolean hasVehicle = vehicleBookingRepository.findByTripIdOrderByPickupDate(tripId).stream()
                .filter(b -> b.getStatus() != VehicleBooking.BookingStatus.CANCELLED)
                .anyMatch(b -> targetDate != null && b.getPickupDate() != null && b.getDropoffDate() != null
                        && !targetDate.isBefore(b.getPickupDate())
                        && !targetDate.isAfter(b.getDropoffDate()));
        if (hasVehicle) {
            throw new RuntimeException("Cannot remove Day " + lastDay.getDayNumber() + ": Active vehicle booking exists on " + targetDate + ". Please cancel or reassign the booking first.");
        }

        // 2. Guide booking guard
        boolean hasGuide = guideBookingRepository.findByTripIdOrderByStartDate(tripId).stream()
                .filter(b -> b.getStatus() != GuideBooking.BookingStatus.CANCELLED)
                .anyMatch(b -> targetDate != null && b.getStartDate() != null && b.getEndDate() != null
                        && !targetDate.isBefore(b.getStartDate())
                        && !targetDate.isAfter(b.getEndDate()));
        if (hasGuide) {
            throw new RuntimeException("Cannot remove Day " + lastDay.getDayNumber() + ": Active tour guide booking exists on " + targetDate + ". Please cancel or reassign the booking first.");
        }

        // 3. Hotel booking guard
        boolean hasHotel = hotelBookingRepository.findByTripIdOrderByCheckIn(tripId).stream()
                .filter(b -> b.getStatus() != HotelBooking.BookingStatus.CANCELLED)
                .anyMatch(b -> (b.getTripDay() != null && b.getTripDay().getId().equals(lastDay.getId()))
                        || (targetDate != null && b.getCheckIn() != null && b.getCheckOut() != null
                            && !targetDate.isBefore(b.getCheckIn())
                            && !targetDate.isAfter(b.getCheckOut())));
        if (hasHotel) {
            throw new RuntimeException("Cannot remove Day " + lastDay.getDayNumber() + ": Active hotel booking exists on " + targetDate + ". Please cancel or reassign the booking first.");
        }

        // 4. Budget item expense guard
        Optional<Budget> budgetOpt = budgetRepository.findByTripId(tripId);
        if (budgetOpt.isPresent()) {
            Budget budget = budgetOpt.get();
            boolean hasExpense = budget.getItems() != null && budget.getItems().stream().anyMatch(item ->
                    (targetDate != null && item.getDate() != null && item.getDate().isEqual(targetDate))
                    || (item.getNotes() != null && item.getNotes().contains("Day " + lastDay.getDayNumber()))
            );
            if (hasExpense) {
                throw new RuntimeException("Cannot remove Day " + lastDay.getDayNumber() + ": Budget expense items exist on " + targetDate + ". Please delete or reassign them in Budget Tracker first.");
            }
        }

        // Identify previous day
        List<TripDay> sortedDays = days.stream()
                .sorted(Comparator.comparingInt(TripDay::getDayNumber))
                .collect(Collectors.toList());
        TripDay previousDay = sortedDays.get(sortedDays.size() - 2);

        double removedCost = lastDay.getEstimatedDayCost() != null ? lastDay.getEstimatedDayCost() : 0.0;
        double removedCostLkr = removedCost * USD_TO_LKR_RATE;

        trip.getDays().remove(lastDay);
        tripDayRepository.delete(lastDay);

        trip.setEndDate(previousDay.getDate());
        trip.setBudgetAmountLkr(Math.max(0.0, (trip.getBudgetAmountLkr() != null ? trip.getBudgetAmountLkr() : 0.0) - removedCostLkr));
        Trip savedTrip = tripRepository.save(trip);

        if (budgetOpt.isPresent()) {
            Budget budget = budgetOpt.get();
            budget.setTotalBudget(Math.max(0.0, (budget.getTotalBudget() != null ? budget.getTotalBudget() : 0.0) - removedCost));
            if (budget.getCategoryBudgets() != null && !budget.getCategoryBudgets().isEmpty()) {
                BudgetItem.ItemCategory targetCategory = budget.getCategoryBudgets().containsKey(BudgetItem.ItemCategory.MISC)
                        ? BudgetItem.ItemCategory.MISC
                        : (budget.getCategoryBudgets().containsKey(BudgetItem.ItemCategory.ACTIVITY)
                            ? BudgetItem.ItemCategory.ACTIVITY
                            : budget.getCategoryBudgets().keySet().iterator().next());
                double currentVal = budget.getCategoryBudgets().getOrDefault(targetCategory, 0.0);
                budget.getCategoryBudgets().put(targetCategory, Math.max(0.0, currentVal - removedCost));
            }
            budgetRepository.save(budget);
        }

        log.info("Removed Day {} from trip {}: new endDate={}, removedCost={}",
                lastDay.getDayNumber(), tripId, previousDay.getDate(), removedCost);
        return toResponse(savedTrip);
    }
}
