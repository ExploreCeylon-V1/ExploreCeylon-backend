package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import com.exploreceylon.backend.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository            userRepository;
    private final TripRepository            tripRepository;
    private final VehicleRepository         vehicleRepository;
    private final VehicleBookingRepository  vehicleBookingRepository;
    private final TourGuideRepository       guideRepository;
    private final GuideBookingRepository    guideBookingRepository;
    private final DestinationRepository     destinationRepository;
    private final HiddenGemRepository       gemRepository;
    private final EventRepository           eventRepository;
    private final LoginHistoryRepository    loginHistoryRepository;
    private final AdminBookingQueryRepository adminBookingQueryRepository;
    private final AdminReviewQueryRepository adminReviewQueryRepository;
    private final AdminReviewService        adminReviewService;

    private static final double COMMISSION_RATE = 0.15;

    // ── Dashboard Stats ────────────────────────────────────
    // Every figure below is a COUNT/SUM aggregate query — the old version
    // called vehicleBookingRepository.findAll()/guideBookingRepository
    // .findAll()/tripRepository.findAll() and summed/filtered/counted in
    // Java, which loads the entire table on every dashboard load.
    public DashboardStatsResponse getDashboardStats() {
        log.info("Fetching dashboard stats");

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();
        long verifiedUsers = userRepository.countByEmailVerifiedTrue();
        long newUsersLast30Days = userRepository.countByCreatedAtAfter(LocalDateTime.now().minusDays(30));

        long activeTrips = tripRepository.countByStatus(Trip.TripStatus.CONFIRMED)
                + tripRepository.countByStatus(Trip.TripStatus.DRAFT);
        long tripsCreated = tripRepository.count();

        long totalVehicles = vehicleRepository.count();
        long totalGuides = guideRepository.count();

        long vehicleBookingCount = vehicleBookingRepository.count();
        long guideBookingCount = guideBookingRepository.count();

        double vehicleRevenue = vehicleBookingRepository.sumAllRevenue();
        double guideRevenue = guideBookingRepository.sumAllRevenue();
        double totalRevenue = vehicleRevenue + guideRevenue;
        double totalCommission = totalRevenue * COMMISSION_RATE;

        long pendingBookings = vehicleBookingRepository.countByStatus(VehicleBooking.BookingStatus.PENDING_PAYMENT)
                + guideBookingRepository.countByStatus(GuideBooking.BookingStatus.PENDING_PAYMENT);

        long totalDestinations = destinationRepository.count();
        long totalGems = gemRepository.count();
        long totalEvents = eventRepository.count();
        long totalReviews = adminReviewQueryRepository.countAll();

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalBookings(vehicleBookingCount + guideBookingCount)
                .totalRevenue(round(totalRevenue))
                .activeTrips(activeTrips)
                .totalVehicles(totalVehicles)
                .totalGuides(totalGuides)
                .totalDestinations(totalDestinations)
                .totalGems(totalGems)
                .totalEvents(totalEvents)
                .vehicleBookings(vehicleBookingCount)
                .guideBookings(guideBookingCount)
                .pendingBookings(pendingBookings)
                .vehicleRevenue(round(vehicleRevenue))
                .guideRevenue(round(guideRevenue))
                .totalCommission(round(totalCommission))
                .activeUsers(activeUsers)
                .verifiedUsers(verifiedUsers)
                .newUsersLast30Days(newUsersLast30Days)
                .tripsCreated(tripsCreated)
                .totalReviews(totalReviews)
                .pendingReviews(0L)
                .build();
    }

    // ── Recent Activity ─────────────────────────────────────
    public RecentActivityResponse getRecentActivity() {
        List<RecentActivityResponse.Registration> registrations = userRepository
                .findTop5ByOrderByCreatedAtDesc().stream()
                .map(u -> RecentActivityResponse.Registration.builder()
                        .id(u.getId()).name(u.getName()).email(u.getEmail())
                        .createdAt(u.getCreatedAt()).build())
                .collect(Collectors.toList());

        List<RecentActivityResponse.TripActivity> trips = tripRepository
                .findRecentWithUser(PageRequest.of(0, 5)).stream()
                .map(t -> RecentActivityResponse.TripActivity.builder()
                        .id(t.getId()).title(t.getTitle())
                        .userName(t.getUser().getName())
                        .createdAt(t.getCreatedAt()).build())
                .collect(Collectors.toList());

        List<AdminBookingResponse> bookings = getAllBookings(
                null, null, null, null, null, null, null,
                "createdAt", "desc", 0, 5).getContent();

        List<AdminReviewResponse> reviews = adminReviewService
                .getAllReviews(null, null, null, null, null, "createdAt", "desc", 0, 5)
                .getContent();

        return RecentActivityResponse.builder()
                .recentRegistrations(registrations)
                .recentTrips(trips)
                .recentBookings(bookings)
                .recentReviews(reviews)
                .build();
    }

    // ── Top Lists ────────────────────────────────────────────
    public TopListsResponse getTopLists() {
        List<TopListsResponse.TopDestination> topDestinations = destinationRepository
                .findTop10ByOrderByReviewCountDesc().stream()
                .limit(5)
                .map(d -> TopListsResponse.TopDestination.builder()
                        .id(d.getId()).name(d.getName())
                        .rating(d.getRating()).reviewCount(d.getReviewCount())
                        .build())
                .collect(Collectors.toList());

        List<TopListsResponse.TopProvider> topGuides = mapTopProviders(
                guideBookingRepository.topGuidesByBookingCount(PageRequest.of(0, 5)),
                ids -> guideRepository.findAllById(ids), TourGuide::getId, TourGuide::getFullName);

        List<TopListsResponse.TopProvider> topVehicles = mapTopProviders(
                vehicleBookingRepository.topVehiclesByBookingCount(PageRequest.of(0, 5)),
                ids -> vehicleRepository.findAllById(ids), Vehicle::getId, Vehicle::getName);

        return TopListsResponse.builder()
                .topDestinations(topDestinations)
                .topGuides(topGuides)
                .topVehicles(topVehicles)
                .build();
    }

    // ── Bulk Booking Status Update ──────────────────────────
    // Batches VEHICLE and GUIDE refs into one findAllById()+saveAll() each
    // instead of one update per booking.
    public int bulkUpdateBookingStatus(List<BulkBookingStatusRequest.BookingRef> items, String status) {
        List<Long> vehicleIds = items.stream()
                .filter(i -> "VEHICLE".equalsIgnoreCase(i.getType()))
                .map(BulkBookingStatusRequest.BookingRef::getId)
                .collect(Collectors.toList());
        List<Long> guideIds = items.stream()
                .filter(i -> "GUIDE".equalsIgnoreCase(i.getType()))
                .map(BulkBookingStatusRequest.BookingRef::getId)
                .collect(Collectors.toList());

        int updated = 0;
        if (!vehicleIds.isEmpty()) {
            VehicleBooking.BookingStatus newStatus = VehicleBooking.BookingStatus.valueOf(status.toUpperCase());
            List<VehicleBooking> bookings = vehicleBookingRepository.findAllById(vehicleIds);
            bookings.forEach(b -> b.setStatus(newStatus));
            vehicleBookingRepository.saveAll(bookings);
            updated += bookings.size();
        }
        if (!guideIds.isEmpty()) {
            GuideBooking.BookingStatus newStatus = GuideBooking.BookingStatus.valueOf(status.toUpperCase());
            List<GuideBooking> bookings = guideBookingRepository.findAllById(guideIds);
            bookings.forEach(b -> b.setStatus(newStatus));
            guideBookingRepository.saveAll(bookings);
            updated += bookings.size();
        }
        log.info("Bulk booking status update: {} bookings -> {}", updated, status);
        return updated;
    }

    // Shared by getTopLists' guide/vehicle branches: turns a
    // [id, count] aggregate result into named Top Provider entries via one
    // batch findAllById() instead of one lookup per row.
    private <T> List<TopListsResponse.TopProvider> mapTopProviders(
            List<Object[]> countRows,
            java.util.function.Function<List<Long>, List<T>> batchFetch,
            java.util.function.Function<T, Long> idFn,
            java.util.function.Function<T, String> nameFn) {

        List<Long> ids = countRows.stream().map(row -> (Long) row[0]).collect(Collectors.toList());
        Map<Long, T> byId = batchFetch.apply(ids).stream()
                .collect(Collectors.toMap(idFn, e -> e));

        return countRows.stream()
                .map(row -> {
                    Long id = (Long) row[0];
                    T entity = byId.get(id);
                    if (entity == null) return null;
                    return TopListsResponse.TopProvider.builder()
                            .id(id)
                            .name(nameFn.apply(entity))
                            .bookingCount((Long) row[1])
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── All Bookings — unified, searchable, filterable, paginated (DB-level) ──
    // Vehicle and guide bookings are two separate tables; AdminBookingQueryRepository
    // runs one UNION ALL query so filtering/sorting/paging all execute in
    // PostgreSQL instead of loading both tables into Java on every request.
    public PageResponse<AdminBookingResponse> getAllBookings(
            String type, String status, String search,
            String customer, String provider,
            LocalDate dateFrom, LocalDate dateTo,
            String sortBy, String sortDir, int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);

        AdminBookingQueryRepository.Result result = adminBookingQueryRepository.search(
                type, status, search, customer, provider, dateFrom, dateTo,
                sortBy, sortDir, safePage, safeSize);

        List<AdminBookingResponse> content = result.rows().stream()
                .map(r -> AdminBookingResponse.builder()
                        .id(r.id())
                        .type(r.type())
                        .status(r.status())
                        .customerId(r.customerId())
                        .customerName(r.customerName())
                        .customerEmail(r.customerEmail())
                        .providerId(r.providerId())
                        .providerName(r.providerName())
                        .tripId(r.tripId())
                        .startDate(r.startDate())
                        .endDate(r.endDate())
                        .totalCost(r.totalCost())
                        .createdAt(r.createdAt())
                        .build())
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil(result.totalElements() / (double) safeSize);

        return PageResponse.<AdminBookingResponse>builder()
                .content(content)
                .totalElements(result.totalElements())
                .totalPages(totalPages)
                .page(safePage)
                .size(safeSize)
                .build();
    }

    // ── Revenue Summary ────────────────────────────────────
    public RevenueResponse getRevenueSummary(String period) {
        List<VehicleBooking> vBookings =
                vehicleBookingRepository.findAll();
        List<GuideBooking>   gBookings =
                guideBookingRepository.findAll();

        double vehicleRevenue = vBookings.stream()
                .mapToDouble(VehicleBooking::getTotalCost).sum();
        double guideRevenue   = gBookings.stream()
                .mapToDouble(GuideBooking::getTotalCost).sum();
        double totalRevenue   = vehicleRevenue + guideRevenue;
        double totalCommission= totalRevenue * COMMISSION_RATE;

        // This month revenue
        int currentMonth = LocalDateTime.now().getMonthValue();
        double thisMonth = vBookings.stream()
                .filter(b -> b.getPickupDate() != null
                        && b.getPickupDate().getMonthValue()
                        == currentMonth)
                .mapToDouble(VehicleBooking::getTotalCost).sum()
                + gBookings.stream()
                .filter(b -> b.getStartDate() != null
                        && b.getStartDate().getMonthValue()
                        == currentMonth)
                .mapToDouble(GuideBooking::getTotalCost).sum();

        // Monthly breakdown
        List<RevenueResponse.MonthlyData> monthlyData =
                new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            final int month = m;
            double mVehicle = vBookings.stream()
                    .filter(b -> b.getPickupDate() != null
                            && b.getPickupDate().getMonthValue()
                            == month)
                    .mapToDouble(VehicleBooking::getTotalCost).sum();
            double mGuide = gBookings.stream()
                    .filter(b -> b.getStartDate() != null
                            && b.getStartDate().getMonthValue()
                            == month)
                    .mapToDouble(GuideBooking::getTotalCost).sum();

            monthlyData.add(RevenueResponse.MonthlyData.builder()
                    .month(Month.of(m).getDisplayName(
                            TextStyle.SHORT, Locale.ENGLISH))
                    .vehicleRevenue(round(mVehicle))
                    .guideRevenue(round(mGuide))
                    .total(round(mVehicle + mGuide))
                    .build());
        }

        return RevenueResponse.builder()
                .totalRevenue(round(totalRevenue))
                .vehicleRevenue(round(vehicleRevenue))
                .guideRevenue(round(guideRevenue))
                .totalCommission(round(totalCommission))
                .thisMonthRevenue(round(thisMonth))
                .commissionRate(COMMISSION_RATE)
                .monthlyData(monthlyData)
                .build();
    }

    // ── Vehicle Stats ──────────────────────────────────────
    public VehicleStatsResponse getVehicleStats() {
        long total     = vehicleRepository.count();
        long available = vehicleRepository
                .findByAvailableTrue().size();
        long booked    = total - available;

        List<VehicleBooking> bookings =
                vehicleBookingRepository.findAll();
        double revenue    = bookings.stream()
                .mapToDouble(VehicleBooking::getTotalCost).sum();
        double commission = revenue * COMMISSION_RATE;

        return VehicleStatsResponse.builder()
                .totalVehicles(total)
                .availableVehicles(available)
                .bookedVehicles(booked)
                .totalRevenue(round(revenue))
                .totalCommission(round(commission))
                .build();
    }

    // ── Guide Stats ────────────────────────────────────────
    public GuideStatsResponse getGuideStats() {
        long total     = guideRepository.count();
        long available = guideRepository
                .findByVerifiedTrueAndAvailableTrueOrderByRatingDesc()
                .size();

        int currentMonth = LocalDateTime.now().getMonthValue();
        long bookedToday = guideBookingRepository.findAll()
                .stream()
                .filter(b -> b.getStatus() ==
                        GuideBooking.BookingStatus.CONFIRMED
                        && b.getStartDate() != null
                        && b.getStartDate().getMonthValue()
                        == currentMonth)
                .count();

        List<GuideBooking> bookings =
                guideBookingRepository.findAll();
        double revenue    = bookings.stream()
                .mapToDouble(GuideBooking::getTotalCost).sum();
        double commission = revenue * COMMISSION_RATE;

        return GuideStatsResponse.builder()
                .totalGuides(total)
                .availableGuides(available)
                .bookedToday(bookedToday)
                .totalRevenue(round(revenue))
                .totalCommission(round(commission))
                .build();
    }

    // ── Users — search / filter / sort / paginate (DB-level) ──────────
    // Filtering, sorting, and paging all execute as one SQL query via
    // JpaSpecificationExecutor + Pageable — no findAll()-then-filter-in-
    // Java, so this scales past however many users end up in the table.
    private static final java.util.Map<String, String> USER_SORT_FIELDS = java.util.Map.of(
            "name", "name", "email", "email", "role", "role", "createdAt", "createdAt");

    public PageResponse<UserResponse> getAllUsers(
            String search, User.Role role, Boolean active,
            Boolean emailVerified, Boolean phoneVerified,
            String sortBy, String sortDir, int page, int size) {

        String sortField = USER_SORT_FIELDS.getOrDefault(sortBy, "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(direction, sortField));

        Specification<User> spec = UserSpecifications.withFilters(search, role, active, emailVerified, phoneVerified);
        Page<User> result = userRepository.findAll(spec, pageable);

        List<UserResponse> content = toUserResponses(result.getContent());

        return PageResponse.<UserResponse>builder()
                .content(content)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .page(result.getNumber())
                .size(result.getSize())
                .build();
    }

    // ── User Detail ─────────────────────────────────────────
    public UserResponse getUserDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toUserResponse(user);
    }

    // ── Activate User ───────────────────────────────────────
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setActive(true);
        userRepository.save(user);
        log.info("User activated: {}", user.getEmail());
    }

    // ── Deactivate User ────────────────────────────────────
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "User not found: " + id));
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", user.getEmail());
    }

    // ── Bulk Activate/Deactivate ────────────────────────────
    // One findAllById + one saveAll instead of N single-user round trips.
    public int bulkSetUserActive(List<Long> ids, boolean active) {
        List<User> users = userRepository.findAllById(ids);
        users.forEach(u -> u.setActive(active));
        userRepository.saveAll(users);
        log.info("Bulk {} {} users", active ? "activated" : "deactivated", users.size());
        return users.size();
    }

    // ── Change Role ──────────────────────────────────────────
    public UserResponse changeUserRole(Long id, User.Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setRole(role);
        userRepository.save(user);
        log.info("User role changed: {} -> {}", user.getEmail(), role);
        return toUserResponse(user);
    }

    // ── Reset Verification Status ───────────────────────────
    // type: EMAIL | PHONE | BOTH — forces the traveler to re-verify.
    public UserResponse resetVerification(Long id, String type) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        String normalized = type == null ? "BOTH" : type.toUpperCase();
        if (normalized.equals("EMAIL") || normalized.equals("BOTH")) {
            user.setEmailVerified(false);
        }
        if (normalized.equals("PHONE") || normalized.equals("BOTH")) {
            user.setPhoneVerified(false);
        }
        userRepository.save(user);
        log.info("Verification reset ({}) for user: {}", normalized, user.getEmail());
        return toUserResponse(user);
    }

    // ── Helpers ────────────────────────────────────────────
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private UserResponse toUserResponse(User u) {
        UserResponse res = new UserResponse();
        res.setId(u.getId());
        res.setName(u.getName());
        res.setEmail(u.getEmail());
        res.setRole(u.getRole());
        res.setNationality(u.getNationality());
        res.setLanguage(u.getLanguage());
        res.setPhone(u.getPhone());
        res.setCreatedAt(u.getCreatedAt());
        res.setActive(Boolean.TRUE.equals(u.getActive()));
        res.setEmailVerified(Boolean.TRUE.equals(u.getEmailVerified()));
        res.setPhoneVerified(Boolean.TRUE.equals(u.getPhoneVerified()));
        res.setTripCount(tripRepository.countByUserId(u.getId()));
        res.setVehicleBookingCount(vehicleBookingRepository.findByUserIdOrderByCreatedAtDesc(u.getId()).size());
        res.setGuideBookingCount(guideBookingRepository.findByUserIdOrderByCreatedAtDesc(u.getId()).size());
        loginHistoryRepository.findByUserOrderByCreatedAtDesc(u)
                .stream().findFirst()
                .ifPresent(lh -> res.setLastLoginAt(lh.getCreatedAt()));
        return res;
    }

    // Batch version of toUserResponse for a page of users — replaces 4
    // per-row repository calls (trips/vehicle bookings/guide bookings/
    // last login) with 4 total grouped queries for the whole page.
    private List<UserResponse> toUserResponses(List<User> users) {
        if (users.isEmpty()) return List.of();
        List<Long> ids = users.stream().map(User::getId).collect(Collectors.toList());

        Map<Long, Long> tripCounts = toCountMap(tripRepository.countByUserIdIn(ids));
        Map<Long, Long> vehicleBookingCounts = toCountMap(vehicleBookingRepository.countByUserIdIn(ids));
        Map<Long, Long> guideBookingCounts = toCountMap(guideBookingRepository.countByUserIdIn(ids));
        Map<Long, LocalDateTime> lastLogins = new HashMap<>();
        for (Object[] row : loginHistoryRepository.findLastLoginByUserIdIn(ids)) {
            lastLogins.put((Long) row[0], (LocalDateTime) row[1]);
        }

        return users.stream().map(u -> {
            UserResponse res = new UserResponse();
            res.setId(u.getId());
            res.setName(u.getName());
            res.setEmail(u.getEmail());
            res.setRole(u.getRole());
            res.setNationality(u.getNationality());
            res.setLanguage(u.getLanguage());
            res.setPhone(u.getPhone());
            res.setCreatedAt(u.getCreatedAt());
            res.setActive(Boolean.TRUE.equals(u.getActive()));
            res.setEmailVerified(Boolean.TRUE.equals(u.getEmailVerified()));
            res.setPhoneVerified(Boolean.TRUE.equals(u.getPhoneVerified()));
            res.setTripCount(tripCounts.getOrDefault(u.getId(), 0L));
            res.setVehicleBookingCount(vehicleBookingCounts.getOrDefault(u.getId(), 0L));
            res.setGuideBookingCount(guideBookingCounts.getOrDefault(u.getId(), 0L));
            res.setLastLoginAt(lastLogins.get(u.getId()));
            return res;
        }).collect(Collectors.toList());
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

}
