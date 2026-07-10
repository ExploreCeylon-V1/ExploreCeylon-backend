package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.BudgetLevel;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.repository.DestinationRepository;
import com.exploreceylon.backend.repository.EventRepository;
import com.exploreceylon.backend.repository.HiddenGemRepository;
import com.exploreceylon.backend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic day-by-day itinerary assembly. This replaces the old
 * approach of letting the LLM pick which stops go on which day: the
 * backend now decides the full corridor-filtered, time-budgeted,
 * slot-labeled day structure BEFORE calling the AI service. The AI
 * service's remaining job (see AiService.generateNarrative) is only to
 * write descriptions/tips for the stops this service already chose.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItineraryAssemblyService {

    private final DestinationRepository destinationRepository;
    private final HiddenGemRepository   hiddenGemRepository;
    private final EventRepository       eventRepository;
    private final com.exploreceylon.backend.repository.LocationRepository locationRepository;

    // ── Corridor detour tuning ──────────────────────────────
    // maxDetourKm = BASE_DETOUR_KM + tripDurationDays * PER_DAY_DETOUR_ALLOWANCE_KM
    private static final double BASE_DETOUR_KM             = 15.0;
    private static final double PER_DAY_DETOUR_ALLOWANCE_KM = 25.0;

    // Destinations/gems within this radius of the trip's own DESTINATION
    // point bypass the style/season filters below. Without this, a
    // real-data travelStyleTags/bestMonths mismatch can exclude the very
    // place the user asked to go to — e.g. Ella (category HILL, tagged
    // PHOTOGRAPHY/RELAXATION) disappearing from a WILDLIFE-style
    // Colombo→Ella trip because its tag doesn't include WILDLIFE. Origin
    // deliberately does NOT get this bypass — a point at the origin
    // already trivially passes the detour/overshoot filters by geometry
    // (detour ≈ 0), so it isn't at risk of being wrongly excluded, and
    // bypassing style there would just pad Day 1 with off-theme content.
    private static final double ENDPOINT_BYPASS_RADIUS_KM = 10.0;

    // How far a candidate is allowed to project past the destination
    // along the origin→destination corridor direction before it's
    // rejected as "beyond the destination" (fix 3). Small enough to
    // exclude genuine overshoot (e.g. Horton Plains past Kandy) but
    // generous enough not to exclude the destination's own immediate
    // surroundings.
    private static final double OVERSHOOT_TOLERANCE_KM = 15.0;

    // ── Day time-budget tuning ──────────────────────────────
    private static final int MAX_SIGHTSEEING_MINUTES_PER_DAY = 480; // ~8h
    private static final int MAX_TRAVEL_MINUTES_PER_DAY       = 240; // ~3-4h
    private static final double AVG_ROAD_SPEED_KMH            = 40.0; // mountain/coastal roads

    // Hard cap on any single stop-to-stop jump in the greedy walk,
    // independent of which day it would land in. At AVG_ROAD_SPEED_KMH
    // (40) and MAX_TRAVEL_MINUTES_PER_DAY (240), a full day's travel
    // budget covers ~160km — this is set to roughly half that, leaving
    // room for same-day stop-to-stop travel on top of the jump itself.
    // Without this cap, a candidate that's the "nearest remaining" point
    // in the overall walk could still be an 80km+ detour from wherever
    // the traveler currently is (see Test 1: Sinharaja placed right
    // after Ella despite being far in the wrong direction).
    private static final double MAX_INTER_DAY_JUMP_KM = 80.0;

    // Day starts ~8am; slot boundaries measured in elapsed minutes since
    // day start (activity + travel time combined). Kept intentionally
    // simple — not meant to be a precise scheduler.
    private static final int MORNING_END_MINUTE   = 240; // 8:00-12:00
    private static final int AFTERNOON_END_MINUTE = 480; // 12:00-16:00
    // beyond AFTERNOON_END_MINUTE => EVENING

    // ── Budget tuning (Phase 5 — real per-day cost) ─────────
    private static final Map<BudgetLevel, Double> ACCOMMODATION_PER_NIGHT_USD = mapOf(
            BudgetLevel.BUDGET, 15.0, BudgetLevel.MID_RANGE, 40.0, BudgetLevel.LUXURY, 100.0);
    private static final Map<BudgetLevel, Double> FOOD_PER_PERSON_PER_DAY_USD = mapOf(
            BudgetLevel.BUDGET, 10.0, BudgetLevel.MID_RANGE, 25.0, BudgetLevel.LUXURY, 60.0);
    private static final double TRANSPORT_USD_PER_KM_PER_PERSON = 0.15;

    // ── Per-category default visit durations (minutes), used when a
    // destination/gem's own visitDurationMinutes hasn't been backfilled.
    private static final Map<Destination.DestinationCategory, Integer> DEST_DEFAULT_MINUTES = mapOf(
            Destination.DestinationCategory.BEACH,     90,
            Destination.DestinationCategory.CULTURAL,  60,
            Destination.DestinationCategory.WILDLIFE, 120,
            Destination.DestinationCategory.HILL,      90,
            Destination.DestinationCategory.SURF,      90,
            Destination.DestinationCategory.ADVENTURE,120,
            Destination.DestinationCategory.HERITAGE,  60,
            Destination.DestinationCategory.RELIGIOUS, 45,
            Destination.DestinationCategory.CITY,      60);
    private static final int DEST_DEFAULT_MINUTES_FALLBACK = 60;

    private static final Map<HiddenGem.GemCategory, Integer> GEM_DEFAULT_MINUTES = mapOf(
            HiddenGem.GemCategory.BEACH,     90,
            HiddenGem.GemCategory.WATERFALL, 60,
            HiddenGem.GemCategory.RUINS,     45,
            HiddenGem.GemCategory.VIEWPOINT, 90,
            HiddenGem.GemCategory.VILLAGE,   45,
            HiddenGem.GemCategory.CAFE,      30,
            HiddenGem.GemCategory.TEMPLE,    45);
    private static final int GEM_DEFAULT_MINUTES_FALLBACK = 45;

    private static <K, V> Map<K, V> mapOf(Object... kv) {
        Map<K, V> map = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            //noinspection unchecked
            map.put((K) kv[i], (V) kv[i + 1]);
        }
        return map;
    }

    public record GeoPoint(double lat, double lng) {}

    public enum StopType { DESTINATION, GEM, EVENT }

    public record PlannedStop(
            StopType type, Long referenceId, String name, String region,
            Double lat, Double lng, Integer visitDurationMinutes,
            Double costUsd, String slot) {}

    public record PlannedDay(
            int dayNumber, LocalDate date, String region,
            List<PlannedStop> stops, double estimatedDayCost) {}

    // ── Geocoding ────────────────────────────────────────────────────
    // Primary source: the dedicated Location gazetteer (cities/towns/
    // districts a traveler types into "from"/"to"), which avoids the
    // false substring matches that plagued keyword-searching the
    // destinations table (e.g. "Galle" matching "Kitulgalle", or
    // "Colombo" matching Kitulgala's description text). Only falls back
    // to the destinations table — preferring an exact name match over
    // the first substring hit — when the query isn't a known place name.
    public Optional<GeoPoint> geocode(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String trimmed = query.trim();

        Optional<GeoPoint> fromGazetteer = locationRepository.findByNameIgnoreCase(trimmed)
                .map(loc -> new GeoPoint(loc.getLatitude(), loc.getLongitude()));
        if (fromGazetteer.isPresent()) return fromGazetteer;

        Optional<GeoPoint> exactDestMatch = destinationRepository.findByNameIgnoreCase(trimmed)
                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                .map(d -> new GeoPoint(d.getLatitude(), d.getLongitude()));
        if (exactDestMatch.isPresent()) return exactDestMatch;

        List<Destination> matches = destinationRepository.searchDestinations(trimmed);
        return matches.stream()
                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                .findFirst()
                .map(d -> new GeoPoint(d.getLatitude(), d.getLongitude()));
    }

    private double maxDetourKm(int tripDurationDays) {
        return BASE_DETOUR_KM + tripDurationDays * PER_DAY_DETOUR_ALLOWANCE_KM;
    }

    private int visitMinutes(Destination d) {
        if (d.getVisitDurationMinutes() != null) return d.getVisitDurationMinutes();
        return DEST_DEFAULT_MINUTES.getOrDefault(d.getCategory(), DEST_DEFAULT_MINUTES_FALLBACK);
    }

    private int visitMinutes(HiddenGem g) {
        if (g.getVisitDurationMinutes() != null) return g.getVisitDurationMinutes();
        return GEM_DEFAULT_MINUTES.getOrDefault(g.getCategory(), GEM_DEFAULT_MINUTES_FALLBACK);
    }

    private boolean matchesStyle(String csvTags, Set<String> selectedStyles) {
        if (csvTags == null || csvTags.isBlank() || selectedStyles.isEmpty()) return true;
        Set<String> tags = Arrays.stream(csvTags.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return tags.stream().anyMatch(selectedStyles::contains);
    }

    private boolean matchesSeason(String csvMonths, Set<String> tripMonthNames) {
        if (csvMonths == null || csvMonths.isBlank() || tripMonthNames.isEmpty()) return true;
        Set<String> months = Arrays.stream(csvMonths.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return months.stream().anyMatch(tripMonthNames::contains);
    }

    private Set<String> monthNamesBetween(LocalDate start, LocalDate end) {
        Set<String> names = new HashSet<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            names.add(cursor.getMonth().name().toLowerCase(Locale.ROOT));
            cursor = cursor.plusMonths(1).withDayOfMonth(1);
            if (names.size() > 12) break; // safety
        }
        return names;
    }

    /** Candidate pool: corridor + budget/style/season filtered destinations and gems. */
    public record CandidatePool(List<Destination> destinations, List<HiddenGem> gems) {}

    public CandidatePool buildCandidatePool(
            GeoPoint origin, GeoPoint destination, int tripDurationDays,
            BudgetLevel budgetLevel, List<String> travelStyles,
            LocalDate startDate, LocalDate endDate) {

        double maxDetour = maxDetourKm(tripDurationDays);
        double directKm  = GeoUtils.distanceKm(origin.lat(), origin.lng(), destination.lat(), destination.lng());
        double midLat = (origin.lat() + destination.lat()) / 2.0;
        double midLng = (origin.lng() + destination.lng()) / 2.0;
        // Coarse SQL radius pre-filter: any point within maxDetour of the
        // route lies within (directKm/2 + maxDetour) of the midpoint —
        // a safe, generous upper bound on the true detour ellipse.
        double coarseRadiusKm = directKm / 2.0 + maxDetour;

        String budgetParam = budgetLevel != null ? budgetLevel.name() : "";
        List<Destination> destCandidates = destinationRepository
                .findWithinRadius(midLat, midLng, coarseRadiusKm, budgetParam);
        List<HiddenGem> gemCandidates = hiddenGemRepository
                .findWithinRadius(midLat, midLng, coarseRadiusKm, budgetParam);

        Set<String> selectedStyles = travelStyles == null ? Set.of() : travelStyles.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> tripMonths = monthNamesBetween(startDate, endDate);

        List<Destination> filteredDest = destCandidates.stream()
                .filter(d -> detourKm(origin, destination, d.getLatitude(), d.getLongitude()) <= maxDetour)
                .filter(d -> !isBeyondDestination(origin, destination, d.getLatitude(), d.getLongitude(), directKm))
                .filter(d -> isNearEndpoint(origin, destination, d.getLatitude(), d.getLongitude())
                        || (matchesStyle(d.getTravelStyleTags(), selectedStyles)
                            && matchesSeason(d.getBestMonths(), tripMonths)))
                .collect(Collectors.toList());

        List<HiddenGem> filteredGems = gemCandidates.stream()
                .filter(g -> detourKm(origin, destination, g.getLatitude(), g.getLongitude()) <= maxDetour)
                .filter(g -> !isBeyondDestination(origin, destination, g.getLatitude(), g.getLongitude(), directKm))
                .filter(g -> isNearEndpoint(origin, destination, g.getLatitude(), g.getLongitude())
                        || (matchesStyle(g.getTravelStyleTags(), selectedStyles)
                            && matchesSeason(g.getSeasonMonths(), tripMonths)))
                .collect(Collectors.toList());

        log.info("Corridor pool: {} destinations, {} gems within {}km detour "
                + "(coarse radius {}km around midpoint)",
                filteredDest.size(), filteredGems.size(), maxDetour, coarseRadiusKm);

        return new CandidatePool(filteredDest, filteredGems);
    }

    private double detourKm(GeoPoint origin, GeoPoint destination, Double lat, Double lng) {
        if (lat == null || lng == null) return Double.MAX_VALUE;
        double toOrigin = GeoUtils.distanceKm(origin.lat(), origin.lng(), lat, lng);
        double toDest   = GeoUtils.distanceKm(lat, lng, destination.lat(), destination.lng());
        double direct   = GeoUtils.distanceKm(origin.lat(), origin.lng(), destination.lat(), destination.lng());
        return toOrigin + toDest - direct;
    }

    // Fix 3: rejects candidates that project past the destination along
    // the origin→destination corridor direction (e.g. Horton Plains
    // sitting past Kandy on a Colombo→Kandy trip) — detourKm alone only
    // measures off-route distance, not overshoot along the route.
    private boolean isBeyondDestination(GeoPoint origin, GeoPoint destination,
                                         Double lat, Double lng, double directKm) {
        if (lat == null || lng == null) return true;
        double projectionKm = GeoUtils.projectionAlongCorridorKm(
                origin.lat(), origin.lng(), destination.lat(), destination.lng(), lat, lng);
        return projectionKm > directKm + OVERSHOOT_TOLERANCE_KM;
    }

    private boolean isNearEndpoint(GeoPoint origin, GeoPoint destination, Double lat, Double lng) {
        if (lat == null || lng == null) return false;
        return GeoUtils.distanceKm(destination.lat(), destination.lng(), lat, lng) <= ENDPOINT_BYPASS_RADIUS_KM;
    }

    private int targetGemCount(int totalDays) {
        if (totalDays <= 1) return 1;
        if (totalDays == 2) return 2;
        return 3;
    }

    /**
     * Builds the full deterministic day structure: greedily bins
     * route-ordered destinations into days under the sightseeing/travel
     * time caps, folds in a deterministic count of hidden gems and any
     * date-matched corridor events, and computes a real per-day cost.
     * The result is what gets sent to the AI service for narrative-only
     * enrichment (Phase 4/6) — stop order, day assignment and referenceId
     * are already final by the time this returns.
     */
    public List<PlannedDay> assemble(
            GeoPoint origin, GeoPoint destination, LocalDate startDate,
            int tripDurationDays, int groupSize, BudgetLevel budgetLevel,
            List<String> travelStyles) {

        CandidatePool pool = buildCandidatePool(
                origin, destination, tripDurationDays, budgetLevel, travelStyles,
                startDate, startDate.plusDays(Math.max(0, tripDurationDays - 1)));

        // ── Combined greedy walk + day binning ──────────────────────────
        // Merged into a single pass (fix 2) so the day boundary is
        // respected AT SELECTION TIME: the nearest-unvisited-candidate
        // search always measures from the actual current position
        // (which carries over across day boundaries, not reset to 0),
        // and any candidate whose jump from there exceeds
        // MAX_INTER_DAY_JUMP_KM is dropped from consideration entirely —
        // never forced into the itinerary on any day.
        List<Destination> remaining = new ArrayList<>(pool.destinations());
        List<List<Destination>> dayBins = new ArrayList<>();
        List<Destination> currentBin = new ArrayList<>();
        int sightseeingMin = 0;
        int travelMin = 0;
        double curLat = origin.lat(), curLng = origin.lng();

        while (!remaining.isEmpty() && dayBins.size() < tripDurationDays) {
            Destination nearest = null;
            double best = Double.MAX_VALUE;
            for (Destination d : remaining) {
                double dist = GeoUtils.distanceKm(curLat, curLng, d.getLatitude(), d.getLongitude());
                if (dist < best) { best = dist; nearest = d; }
            }
            if (nearest == null) break;
            if (best > MAX_INTER_DAY_JUMP_KM) {
                // Too far a jump from wherever we are now — unreachable
                // without breaking the itinerary regardless of which day
                // it would land on. Drop it and keep looking.
                remaining.remove(nearest);
                continue;
            }

            int dur = visitMinutes(nearest);
            int travel = (int) Math.round(best / AVG_ROAD_SPEED_KMH * 60);

            boolean wouldOverflow = !currentBin.isEmpty()
                    && (sightseeingMin + dur > MAX_SIGHTSEEING_MINUTES_PER_DAY
                        || travelMin + travel > MAX_TRAVEL_MINUTES_PER_DAY);

            if (wouldOverflow) {
                dayBins.add(currentBin);
                if (dayBins.size() >= tripDurationDays) break;
                currentBin = new ArrayList<>();
                sightseeingMin = 0;
                travelMin = 0;
            }

            currentBin.add(nearest);
            remaining.remove(nearest);
            sightseeingMin += dur;
            travelMin += travel;
            curLat = nearest.getLatitude();
            curLng = nearest.getLongitude();
        }
        if (!currentBin.isEmpty() && dayBins.size() < tripDurationDays) dayBins.add(currentBin);
        while (dayBins.size() < tripDurationDays) dayBins.add(new ArrayList<>()); // sparse pool → light days

        // ── Guarantee the trip actually arrives at its destination ─────
        // The greedy walk above can legitimately drop the destination
        // itself if every candidate near it is farther than
        // MAX_INTER_DAY_JUMP_KM from wherever the walk last stood (e.g. a
        // style filter thins out the intermediate waypoints, leaving a
        // gap too wide to cross in one hop). An itinerary that never
        // reaches the place the user asked to go to is worse than one
        // that bends the per-day caps slightly on the final day, so if a
        // real Destination entity near the endpoint exists in the pool
        // and isn't already included anywhere, force it onto the last day.
        boolean destinationAlreadyIncluded = dayBins.stream().flatMap(List::stream)
                .anyMatch(d -> GeoUtils.distanceKm(destination.lat(), destination.lng(),
                        d.getLatitude(), d.getLongitude()) <= ENDPOINT_BYPASS_RADIUS_KM);
        if (!destinationAlreadyIncluded) {
            pool.destinations().stream()
                    .filter(d -> GeoUtils.distanceKm(destination.lat(), destination.lng(),
                            d.getLatitude(), d.getLongitude()) <= ENDPOINT_BYPASS_RADIUS_KM)
                    .findFirst()
                    .ifPresent(d -> dayBins.get(tripDurationDays - 1).add(d));
        }

        // ── Gem placement target (count only) — WHICH gem lands on WHICH
        // day is now decided inline inside pass 1's per-day loop below,
        // picking the nearest available gem to that day's actual current
        // position (capped at MAX_INTER_DAY_JUMP_KM). The old approach
        // picked the top-N gems by rating first and spread them evenly
        // across day *indices* with no geography check at all — which
        // could place a gem near the origin on the trip's last day, long
        // after the itinerary had already moved on toward the destination
        // (the same continuity bug fix 2 addresses for destinations,
        // just surfacing via gems instead).
        int gemTarget = Math.min(targetGemCount(tripDurationDays), pool.gems().size());
        List<HiddenGem> availableGems = new ArrayList<>(pool.gems());
        int gemsPlaced = 0;

        // ── Event candidates (corridor + date filtered) ────────────────
        double maxDetour = maxDetourKm(tripDurationDays);
        double midLat = (origin.lat() + destination.lat()) / 2.0;
        double midLng = (origin.lng() + destination.lng()) / 2.0;
        double directKm = GeoUtils.distanceKm(origin.lat(), origin.lng(), destination.lat(), destination.lng());
        double coarseRadiusKm = directKm / 2.0 + maxDetour;
        LocalDate endDate = startDate.plusDays(Math.max(0, tripDurationDays - 1));
        List<Event> corridorEvents = eventRepository.findEventsBetweenDatesWithinRadius(
                startDate, endDate, midLat, midLng, coarseRadiusKm);

        // ── Pass 1: build each day's destination/gem stops (no events
        // yet), tracking a per-day anchor point (last stop's lat/lng, or
        // the day-start point if the day has no stops) so events can
        // later be matched to the geographically closest day (fix 5). ──
        List<List<PlannedStop>> stopsByDay = new ArrayList<>();
        List<GeoPoint> anchorByDay = new ArrayList<>();
        List<GeoPoint> dayStartByDay = new ArrayList<>();
        List<String> regionByDay = new ArrayList<>();

        for (int dayIdx = 0; dayIdx < tripDurationDays; dayIdx++) {
            List<PlannedStop> stops = new ArrayList<>();

            int elapsed = 0;
            double lat = dayIdx == 0 ? origin.lat() : lastPointOfPreviousDay(dayBins, dayIdx, origin).lat();
            double lng = dayIdx == 0 ? origin.lng() : lastPointOfPreviousDay(dayBins, dayIdx, origin).lng();
            GeoPoint dayStart = dayIdx == 0 ? origin : new GeoPoint(lat, lng);
            String region = null;

            for (Destination d : dayBins.get(dayIdx)) {
                double distKm = GeoUtils.distanceKm(lat, lng, d.getLatitude(), d.getLongitude());
                int travel = (int) Math.round(distKm / AVG_ROAD_SPEED_KMH * 60);
                elapsed += travel;
                String slot = slotFor(elapsed);
                elapsed += visitMinutes(d);
                stops.add(new PlannedStop(StopType.DESTINATION, d.getId(), d.getName(),
                        d.getDistrict(), d.getLatitude(), d.getLongitude(),
                        visitMinutes(d), d.getEntryFeeUsd(), slot));
                lat = d.getLatitude(); lng = d.getLongitude();
                if (region == null) region = d.getDistrict();
            }

            // Pick the nearest still-available gem to this day's current
            // position, capped at MAX_INTER_DAY_JUMP_KM — never the
            // highest-rated gem regardless of where it is. Stop once the
            // trip-wide gem target has been reached.
            if (gemsPlaced < gemTarget) {
                HiddenGem bestGem = null;
                double bestGemDist = Double.MAX_VALUE;
                for (HiddenGem g : availableGems) {
                    if (g.getLatitude() == null || g.getLongitude() == null) continue;
                    double dist = GeoUtils.distanceKm(lat, lng, g.getLatitude(), g.getLongitude());
                    if (dist <= MAX_INTER_DAY_JUMP_KM && dist < bestGemDist) {
                        bestGemDist = dist;
                        bestGem = g;
                    }
                }
                if (bestGem != null) {
                    availableGems.remove(bestGem);
                    gemsPlaced++;
                    int travel = (int) Math.round(bestGemDist / AVG_ROAD_SPEED_KMH * 60);
                    elapsed += travel;
                    String slot = slotFor(elapsed);
                    elapsed += visitMinutes(bestGem);
                    stops.add(new PlannedStop(StopType.GEM, bestGem.getId(), bestGem.getTitle(),
                            bestGem.getDistrict(), bestGem.getLatitude(), bestGem.getLongitude(),
                            visitMinutes(bestGem), bestGem.getEntryFeeUsd(), slot));
                    lat = bestGem.getLatitude(); lng = bestGem.getLongitude();
                    if (region == null) region = bestGem.getDistrict();
                }
            }

            stopsByDay.add(stops);
            anchorByDay.add(new GeoPoint(lat, lng));
            dayStartByDay.add(dayStart);
            regionByDay.add(region);
        }

        // ── Pass 2: assign each event to exactly one day — the closest
        // (by anchor point) among the days whose date falls within the
        // event's date range — instead of independently re-matching the
        // same event on every overlapping day. ─────────────────────────
        Set<Long> assignedEventIds = new HashSet<>();
        for (Event e : corridorEvents) {
            if (e.getId() != null && assignedEventIds.contains(e.getId())) continue;
            int bestDayIdx = -1;
            double bestDist = Double.MAX_VALUE;
            for (int dayIdx = 0; dayIdx < tripDurationDays; dayIdx++) {
                LocalDate date = startDate.plusDays(dayIdx);
                if (date.isBefore(e.getStartDate()) || date.isAfter(e.getEndDate())) continue;
                if (e.getLatitude() == null || e.getLongitude() == null) continue;
                GeoPoint anchor = anchorByDay.get(dayIdx);
                double dist = GeoUtils.distanceKm(anchor.lat(), anchor.lng(), e.getLatitude(), e.getLongitude());
                if (dist < bestDist) { bestDist = dist; bestDayIdx = dayIdx; }
            }
            if (bestDayIdx < 0) continue; // no candidate day — skip defensively
            stopsByDay.get(bestDayIdx).add(new PlannedStop(StopType.EVENT, e.getId(), e.getTitle(),
                    e.getRegion(), e.getLatitude(), e.getLongitude(),
                    0, e.getEstimatedCost(), "EVENING"));
            if (e.getId() != null) assignedEventIds.add(e.getId());
        }

        // ── Assemble final PlannedDay list ──────────────────────────
        List<PlannedDay> result = new ArrayList<>();
        for (int dayIdx = 0; dayIdx < tripDurationDays; dayIdx++) {
            LocalDate date = startDate.plusDays(dayIdx);
            List<PlannedStop> stops = stopsByDay.get(dayIdx);
            GeoPoint anchor = anchorByDay.get(dayIdx);
            String region = regionByDay.get(dayIdx);

            double dayCost = computeDayCost(stops, budgetLevel, groupSize,
                    anchor.lat(), anchor.lng(), dayStartByDay.get(dayIdx));

            result.add(new PlannedDay(dayIdx + 1, date,
                    region != null ? region : (dayIdx == 0 ? "Departure area" : "En route"),
                    stops, dayCost));
        }
        return result;
    }

    private GeoPoint lastPointOfPreviousDay(List<List<Destination>> dayBins, int dayIdx, GeoPoint origin) {
        for (int i = dayIdx - 1; i >= 0; i--) {
            List<Destination> bin = dayBins.get(i);
            if (!bin.isEmpty()) {
                Destination last = bin.get(bin.size() - 1);
                return new GeoPoint(last.getLatitude(), last.getLongitude());
            }
        }
        return origin;
    }

    private String slotFor(int elapsedMinutes) {
        if (elapsedMinutes <= MORNING_END_MINUTE) return "MORNING";
        if (elapsedMinutes <= AFTERNOON_END_MINUTE) return "AFTERNOON";
        return "EVENING";
    }

    // ── Phase 5: real per-day budget from actual chosen stops ──────────
    // NOTE: no automatic destination-swap-on-overbudget logic here — that
    // needs a numeric $ budget target, which doesn't exist yet (budgetRange
    // today is only a tier enum). Follow-up once that field lands.
    private double computeDayCost(List<PlannedStop> stops, BudgetLevel tripBudgetLevel,
                                   int groupSize, double dayEndLat, double dayEndLng,
                                   GeoPoint dayStart) {
        double stopsCost = stops.stream()
                .map(PlannedStop::costUsd)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        BudgetLevel effectiveTier = tripBudgetLevel != null ? tripBudgetLevel : BudgetLevel.MID_RANGE;

        double accommodation = ACCOMMODATION_PER_NIGHT_USD.get(effectiveTier) * groupSize;
        double food = FOOD_PER_PERSON_PER_DAY_USD.get(effectiveTier) * groupSize;

        double dayTravelKm = 0.0;
        double lat = dayStart.lat(), lng = dayStart.lng();
        for (PlannedStop s : stops) {
            if (s.lat() != null && s.lng() != null) {
                dayTravelKm += GeoUtils.distanceKm(lat, lng, s.lat(), s.lng());
                lat = s.lat(); lng = s.lng();
            }
        }
        double transport = dayTravelKm * TRANSPORT_USD_PER_KM_PER_PERSON * groupSize;

        return Math.round((stopsCost + accommodation + food + transport) * 100.0) / 100.0;
    }
}
