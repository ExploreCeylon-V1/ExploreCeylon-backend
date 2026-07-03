package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.trip.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.repository.*;
import com.exploreceylon.backend.util.GeoUtils;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final DestinationRepository destinationRepository;
    private final HiddenGemRepository   hiddenGemRepository;
    private final EventRepository       eventRepository;
    private final VehicleBookingRepository vehicleBookingRepository;
    private final GuideBookingRepository   guideBookingRepository;
    private final BudgetRepository         budgetRepository;

    // Splits a leading name off strings like "Gem Name — description"
    // or "Gem Name - description" as returned by the AI for hiddenGem.
    private String extractLeadingName(String raw) {
        if (raw == null) return null;
        String name = raw.split("—", 2)[0].split(" - ", 2)[0].trim();
        return name.isEmpty() ? null : name;
    }

    // Resolves an AI-returned gem string back to a real DB row. Tries the
    // full raw string first — some real gem titles legitimately contain
    // an em dash (e.g. "Secret Tea Estate Cafe — Nuwara Eliya"), so
    // truncating before matching would miss them — then falls back to
    // the leading-name heuristic for the common "Name — description" case.
    private java.util.Optional<HiddenGem> resolveGem(String raw) {
        if (raw == null) return java.util.Optional.empty();
        java.util.Optional<HiddenGem> exact =
                hiddenGemRepository.findByTitleIgnoreCase(raw.trim());
        if (exact.isPresent()) return exact;
        String leading = extractLeadingName(raw);
        return leading == null ? java.util.Optional.empty()
                : hiddenGemRepository.findByTitleIgnoreCase(leading);
    }

    // Same idea as resolveGem, for AI-returned festival/event strings.
    private java.util.Optional<Event> resolveEvent(String raw) {
        if (raw == null) return java.util.Optional.empty();
        java.util.Optional<Event> exact =
                eventRepository.findByTitleIgnoreCase(raw.trim());
        if (exact.isPresent()) return exact;
        String leading = extractLeadingName(raw);
        return leading == null ? java.util.Optional.empty()
                : eventRepository.findByTitleIgnoreCase(leading);
    }

    // Which days should keep/drop/gain a hidden gem so the trip ends up
    // with a deterministic 2-3 total, instead of trusting the LLM's
    // "inject 1 every 2-3 days" prompt wording (which can yield anywhere
    // from 0 to totalDays gems).
    private record GemPlan(Set<Integer> dropDayIndexes,
                            Map<Integer, HiddenGem> injectedByDayIndex) {}

    private int targetGemCount(int totalDays) {
        if (totalDays <= 1) return 1;
        if (totalDays == 2) return 2;
        return 3;
    }

    private GemPlan planHiddenGems(JsonNode daysArray) {
        int target = targetGemCount(daysArray.size());

        List<Integer> gemDayIndexes = new ArrayList<>();
        Set<String> usedGemNames = new HashSet<>();
        for (int i = 0; i < daysArray.size(); i++) {
            String raw = daysArray.get(i).path("hiddenGem").asText(null);
            String name = (raw != null && !raw.equalsIgnoreCase("null")
                    && !raw.isBlank()) ? extractLeadingName(raw) : null;
            if (name != null) {
                gemDayIndexes.add(i);
                usedGemNames.add(name.toLowerCase());
            }
        }

        Set<Integer> dropDayIndexes = new HashSet<>();
        if (gemDayIndexes.size() > target) {
            dropDayIndexes.addAll(
                    gemDayIndexes.subList(target, gemDayIndexes.size()));
        }

        Map<Integer, HiddenGem> injected = new HashMap<>();
        int needed = target - Math.min(gemDayIndexes.size(), target);
        if (needed > 0) {
            List<Integer> gemlessDayIndexes = new ArrayList<>();
            for (int i = 0; i < daysArray.size(); i++) {
                if (!gemDayIndexes.contains(i)) gemlessDayIndexes.add(i);
            }

            Set<String> dayRegions = new LinkedHashSet<>();
            for (int i = 0; i < daysArray.size(); i++) {
                String r = daysArray.get(i).path("region").asText(null);
                if (r != null && !r.isBlank()) dayRegions.add(r);
            }

            List<HiddenGem> pool = new ArrayList<>();
            for (String region : dayRegions) {
                pool.addAll(hiddenGemRepository
                        .findByDistrictIgnoreCaseAndApprovedTrueOrderByRatingDesc(
                                region));
            }
            if (pool.isEmpty()) {
                pool.addAll(hiddenGemRepository
                        .findByApprovedTrueOrderByRatingDesc());
            }

            List<HiddenGem> fillers = pool.stream()
                    .filter(g -> !usedGemNames.contains(
                            g.getTitle().toLowerCase()))
                    .distinct()
                    .limit(needed)
                    .collect(Collectors.toList());

            if (!gemlessDayIndexes.isEmpty()) {
                double spacing = (double) gemlessDayIndexes.size()
                        / Math.max(1, fillers.size());
                for (int n = 0; n < fillers.size(); n++) {
                    int pos = Math.min((int) Math.floor(n * spacing),
                            gemlessDayIndexes.size() - 1);
                    injected.putIfAbsent(
                            gemlessDayIndexes.get(pos), fillers.get(n));
                }
            }
        }

        return new GemPlan(dropDayIndexes, injected);
    }

    // ── Distance-based ordering ─────────────────────────────
    // A day's item paired with its real coordinates, if the AI's text
    // could be resolved to a DB row. Items without coordinates (e.g.
    // generic activities with no matching destination) are left out of
    // the nearest-neighbor pass and appended in their original order.
    private record ItemBuild(TripDayItem item, Double lat, Double lng) {
        boolean hasCoords() { return lat != null && lng != null; }
    }

    private record DayBuild(TripDay day, List<ItemBuild> items,
                             Double lat, Double lng) {
        boolean hasCoords() { return lat != null && lng != null; }
    }

    // Greedy nearest-neighbor path over points with coordinates, starting
    // from the first element. Points without coordinates are appended at
    // the end, preserving their original relative order.
    private <T> List<T> nearestNeighborOrder(
            List<T> points, java.util.function.Function<T, Double> lat,
            java.util.function.Function<T, Double> lng) {

        List<T> withCoords = points.stream()
                .filter(p -> lat.apply(p) != null && lng.apply(p) != null)
                .collect(Collectors.toList());
        List<T> withoutCoords = points.stream()
                .filter(p -> lat.apply(p) == null || lng.apply(p) == null)
                .collect(Collectors.toList());

        List<T> ordered = new ArrayList<>();
        if (!withCoords.isEmpty()) {
            List<T> remaining = new ArrayList<>(withCoords);
            T current = remaining.remove(0);
            ordered.add(current);
            while (!remaining.isEmpty()) {
                T nearest = null;
                double best = Double.MAX_VALUE;
                for (T candidate : remaining) {
                    double d = GeoUtils.distanceKm(
                            lat.apply(current), lng.apply(current),
                            lat.apply(candidate), lng.apply(candidate));
                    if (d < best) { best = d; nearest = candidate; }
                }
                ordered.add(nearest);
                remaining.remove(nearest);
                current = nearest;
            }
        }
        ordered.addAll(withoutCoords);
        return ordered;
    }

    // Reorders a trip's days by real distance instead of trusting the
    // LLM's own ordering claims. Day 1 (arrival) and the final day
    // (departure) stay fixed in place, per the prompt's own anchoring —
    // only the interior days are reshuffled via nearest-neighbor over
    // each day's item centroid, to reduce backtracking between regions.
    private List<DayBuild> orderInteriorDaysByNearestNeighbor(
            List<DayBuild> days) {
        if (days.size() <= 3) return days;

        DayBuild first = days.get(0);
        DayBuild last = days.get(days.size() - 1);
        List<DayBuild> interior = new ArrayList<>(days.subList(1, days.size() - 1));

        // Seed the chain from whichever interior day is closest to the
        // fixed arrival day, so the path flows outward from Day 1 instead
        // of just picking up wherever the LLM happened to list first.
        if (first.hasCoords()) {
            interior.sort(java.util.Comparator.comparingDouble(d -> d.hasCoords()
                    ? GeoUtils.distanceKm(first.lat(), first.lng(), d.lat(), d.lng())
                    : Double.MAX_VALUE));
        }

        List<DayBuild> orderedInterior = nearestNeighborOrder(
                interior, DayBuild::lat, DayBuild::lng);

        List<DayBuild> result = new ArrayList<>();
        result.add(first);
        result.addAll(orderedInterior);
        result.add(last);
        return result;
    }

    private Double average(List<Double> values) {
        List<Double> nonNull = values.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return nonNull.isEmpty() ? null
                : nonNull.stream().mapToDouble(Double::doubleValue)
                        .average().orElse(0);
    }

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

        // Validate the AI response shape before touching any trip state,
        // so a malformed/hallucinated LLM response fails loudly instead
        // of silently wiping the existing itinerary.
        int expectedDays = (int) (ChronoUnit.DAYS.between(
                req.getStartDate(), req.getEndDate()) + 1);
        JsonNode validatedDaysNode = data.path("days");
        if (!validatedDaysNode.isArray() || validatedDaysNode.isEmpty()) {
            throw new RuntimeException(
                    "AI response contained no itinerary days");
        }
        if (validatedDaysNode.size() != expectedDays) {
            throw new RuntimeException(String.format(
                    "AI response day count mismatch: expected %d, got %d",
                    expectedDays, validatedDaysNode.size()));
        }
        Set<Integer> seenDayNumbers = new HashSet<>();
        for (JsonNode dayNode : validatedDaysNode) {
            int dayNumber = dayNode.path("dayNumber").asInt(-1);
            if (dayNumber < 1 || dayNumber > expectedDays
                    || !seenDayNumbers.add(dayNumber)) {
                throw new RuntimeException(
                        "AI response contained an invalid or duplicate day number: "
                                + dayNumber);
            }
            if (!dayNode.path("locations").isArray()
                    || dayNode.path("locations").isEmpty()) {
                throw new RuntimeException(
                        "AI response day " + dayNumber
                                + " has no locations");
            }
        }

        // Update title with AI's title, but only if the user hasn't
        // already customized it (renamed away from the auto-generated
        // placeholder) since the trip was created.
        String autoTitle = generateTripTitle(
                trip.getFromLocation(), trip.getToLocation(),
                trip.getStartDate(), trip.getEndDate());
        String aiTitle = data.path("tripTitle").asText(null);
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

        // Clear existing days then rebuild from AI response
        trip.getDays().clear();
        tripRepository.save(trip);

        JsonNode daysNode = validatedDaysNode;
        GemPlan gemPlan = planHiddenGems(daysNode);
        List<DayBuild> dayBuilds = new ArrayList<>();

        if (daysNode.isArray()) {
            for (int dayIdx = 0; dayIdx < daysNode.size(); dayIdx++) {
                JsonNode dayNode = daysNode.get(dayIdx);

                TripDay day = TripDay.builder()
                        .trip(trip)
                        .region(dayNode.path("region").asText(null))
                        .theme(dayNode.path("theme").asText(null))
                        .tips(dayNode.path("tips").asText(null))
                        .estimatedDayCost(
                                dayNode.path("estimatedDayCost").asDouble(0))
                        .build();

                List<ItemBuild> itemBuilds = new ArrayList<>();

                // Locations → ACTIVITY items
                JsonNode locations = dayNode.path("locations");
                if (locations.isArray()) {
                    for (JsonNode loc : locations) {
                        String locName = loc.asText();
                        java.util.Optional<Destination> destMatch =
                                destinationRepository.findByNameIgnoreCase(locName);
                        TripDayItem item = TripDayItem.builder()
                                .tripDay(day)
                                .type(TripDayItem.ItemType.ACTIVITY)
                                .title(locName)
                                .referenceId(destMatch
                                        .map(d -> d.getId().toString())
                                        .orElse(null))
                                .cost(0.0).currency("USD")
                                .build();
                        itemBuilds.add(new ItemBuild(item,
                                destMatch.map(Destination::getLatitude).orElse(null),
                                destMatch.map(Destination::getLongitude).orElse(null)));
                    }
                }

                // Hidden gem → GEM item. The day-level count is enforced
                // deterministically by gemPlan (target of 2-3 per trip)
                // rather than trusting the LLM's per-day inclusion.
                HiddenGem injectedGem = gemPlan.injectedByDayIndex().get(dayIdx);
                if (injectedGem != null) {
                    TripDayItem item = TripDayItem.builder()
                            .tripDay(day)
                            .type(TripDayItem.ItemType.GEM)
                            .title("Hidden Gem: " + injectedGem.getTitle())
                            .referenceId(injectedGem.getId().toString())
                            .cost(0.0).currency("USD")
                            .build();
                    itemBuilds.add(new ItemBuild(item,
                            injectedGem.getLatitude(), injectedGem.getLongitude()));
                } else if (!gemPlan.dropDayIndexes().contains(dayIdx)) {
                    String hiddenGem = dayNode.path("hiddenGem").asText(null);
                    if (hiddenGem != null && !hiddenGem.equals("null")
                            && !hiddenGem.isBlank()) {
                        java.util.Optional<HiddenGem> gemMatch = resolveGem(hiddenGem);
                        TripDayItem item = TripDayItem.builder()
                                .tripDay(day)
                                .type(TripDayItem.ItemType.GEM)
                                .title("Hidden Gem: " + hiddenGem)
                                .referenceId(gemMatch
                                        .map(g -> g.getId().toString())
                                        .orElse(null))
                                .cost(0.0).currency("USD")
                                .build();
                        itemBuilds.add(new ItemBuild(item,
                                gemMatch.map(HiddenGem::getLatitude).orElse(null),
                                gemMatch.map(HiddenGem::getLongitude).orElse(null)));
                    }
                }

                // Festival event → ACTIVITY item
                String festivalEvent =
                        dayNode.path("festivalEvent").asText(null);
                if (festivalEvent != null && !festivalEvent.equals("null")
                        && !festivalEvent.isBlank()) {
                    java.util.Optional<Event> eventMatch = resolveEvent(festivalEvent);
                    TripDayItem item = TripDayItem.builder()
                            .tripDay(day)
                            .type(TripDayItem.ItemType.ACTIVITY)
                            .title("Festival: " + festivalEvent)
                            .referenceId(eventMatch
                                    .map(e -> e.getId().toString())
                                    .orElse(null))
                            .cost(0.0).currency("USD")
                            .notes("Special festival event on this day!")
                            .build();
                    itemBuilds.add(new ItemBuild(item,
                            eventMatch.map(Event::getLatitude).orElse(null),
                            eventMatch.map(Event::getLongitude).orElse(null)));
                }

                // Order this day's stops by real distance (nearest-neighbor)
                // instead of trusting the order the LLM listed them in.
                List<ItemBuild> orderedItems = nearestNeighborOrder(
                        itemBuilds, ItemBuild::lat, ItemBuild::lng);
                for (int i = 0; i < orderedItems.size(); i++) {
                    orderedItems.get(i).item().setOrderIndex(i);
                }
                day.getItems().addAll(orderedItems.stream()
                        .map(ItemBuild::item).collect(Collectors.toList()));

                Double centroidLat = average(orderedItems.stream()
                        .map(ItemBuild::lat).collect(Collectors.toList()));
                Double centroidLng = average(orderedItems.stream()
                        .map(ItemBuild::lng).collect(Collectors.toList()));

                dayBuilds.add(new DayBuild(day, orderedItems, centroidLat, centroidLng));
            }
        }

        // Reorder days by real distance between regions instead of
        // trusting the LLM's own sequencing (day 1 arrival and the
        // final departure day stay anchored in place).
        List<DayBuild> orderedDays = orderInteriorDaysByNearestNeighbor(dayBuilds);

        int dayNumber = 1;
        LocalDate dayDate = req.getStartDate();
        for (DayBuild db : orderedDays) {
            TripDay day = db.day();
            day.setDayNumber(dayNumber++);
            day.setDate(dayDate);
            dayDate = dayDate.plusDays(1);
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

        // Deterministic per-day cost, replacing the LLM's own freeform
        // per-day cost guess with the rate-table-based budget estimator.
        try {
            JsonNode budgetResp = aiService.estimateBudget(
                    expectedDays, budgetRange, req.getGroupSize(), regions)
                    .block();
            JsonNode budgetData = budgetResp != null
                    ? budgetResp.path("data") : null;
            double groupTotal = budgetData != null
                    ? budgetData.path("group_total").asDouble(-1) : -1;
            if (groupTotal >= 0 && expectedDays > 0) {
                double perDay = Math.round(
                        (groupTotal / expectedDays) * 100.0) / 100.0;
                for (TripDay day : trip.getDays()) {
                    day.setEstimatedDayCost(perDay);
                }
            }
        } catch (Exception e) {
            log.warn("Budget estimate failed, keeping AI-provided day costs: {}",
                    e.getMessage());
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
