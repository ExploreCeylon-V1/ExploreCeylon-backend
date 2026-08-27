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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final AdminPaymentQueryRepository adminPaymentQueryRepository;
    private final AdminReviewQueryRepository adminReviewQueryRepository;
    private final AdminReviewService        adminReviewService;
    private final VehiclePaymentRepository  vehiclePaymentRepository;
    private final GuidePaymentRepository    guidePaymentRepository;
    private final NotificationService       notificationService;
    private final PasswordEncoder           passwordEncoder;

    private static final double COMMISSION_RATE = 0.15;

    // Re-confirms it's really the acting admin (not just a still-open browser tab)
    // before role changes or activate/deactivate — the password checked here is the
    // ACTING admin's own, never the target user's.
    private void verifyAdminPassword(User admin, String rawPassword) {
        if (admin.getPassword() == null
                || rawPassword == null || rawPassword.isBlank()
                || !passwordEncoder.matches(rawPassword, admin.getPassword())) {
            throw new RuntimeException("Incorrect password");
        }
    }

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

        long vehicleBookingCount = vehicleBookingRepository.countByStatus(VehicleBooking.BookingStatus.CONFIRMED);
        long guideBookingCount = guideBookingRepository.countByStatus(GuideBooking.BookingStatus.CONFIRMED);
        long totalBookings = vehicleBookingCount + guideBookingCount;

        Double completedVehicleRevenue = vehicleBookingRepository.sumTotalCostByStatus(VehicleBooking.BookingStatus.COMPLETED);
        double vehicleRevenue = completedVehicleRevenue != null ? completedVehicleRevenue : 0.0;

        Double completedGuideRevenue = guideBookingRepository.sumTotalCostByStatus(GuideBooking.BookingStatus.COMPLETED);
        double guideRevenue = completedGuideRevenue != null ? completedGuideRevenue : 0.0;

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
                .totalBookings(totalBookings)
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

        Double completedRevenue = vehicleBookingRepository.sumTotalCostByStatus(VehicleBooking.BookingStatus.COMPLETED);
        double revenue    = completedRevenue != null ? completedRevenue : 0.0;
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

        Double completedRevenue = guideBookingRepository.sumTotalCostByStatus(GuideBooking.BookingStatus.COMPLETED);
        double revenue    = completedRevenue != null ? completedRevenue : 0.0;
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
    public void activateUser(Long id, User admin, String password) {
        verifyAdminPassword(admin, password);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setActive(true);
        userRepository.save(user);
        log.info("User activated: {} (by {})", user.getEmail(), admin.getEmail());
    }

    // ── Deactivate User ────────────────────────────────────
    public void deactivateUser(Long id, User admin, String password) {
        verifyAdminPassword(admin, password);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "User not found: " + id));
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: {} (by {})", user.getEmail(), admin.getEmail());
    }

    // ── Bulk Activate/Deactivate ────────────────────────────
    // One findAllById + one saveAll instead of N single-user round trips.
    public int bulkSetUserActive(List<Long> ids, boolean active, User admin, String password) {
        verifyAdminPassword(admin, password);
        List<User> users = userRepository.findAllById(ids);
        users.forEach(u -> u.setActive(active));
        userRepository.saveAll(users);
        log.info("Bulk {} {} users (by {})", active ? "activated" : "deactivated", users.size(), admin.getEmail());
        return users.size();
    }

    // ── Change Role ──────────────────────────────────────────
    public UserResponse changeUserRole(Long id, User.Role role, User admin, String password) {
        verifyAdminPassword(admin, password);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        // Refuse to demote the last remaining admin — that would lock everyone out of
        // the admin panel with no way back in short of a direct DB edit.
        if (user.getRole() == User.Role.ADMIN && role != User.Role.ADMIN
                && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw new RuntimeException("Cannot remove the last remaining admin");
        }

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
        res.setKycStatus(u.getKycStatus() != null ? u.getKycStatus() : User.KycStatus.NOT_SUBMITTED);
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
            res.setKycStatus(u.getKycStatus() != null ? u.getKycStatus() : User.KycStatus.NOT_SUBMITTED);
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

    // ── Admin Payment Management ──────────────────────────

    public PageResponse<AdminPaymentResponse> getAllPayments(
            String type, String completionStatus, String search,
            LocalDate dateFrom, LocalDate dateTo,
            String sortBy, String sortDir, int page, int size) {

        AdminPaymentQueryRepository.Result result = adminPaymentQueryRepository.search(
                type, completionStatus, search, dateFrom, dateTo,
                sortBy, sortDir, page, size);

        LocalDate today = LocalDate.now();
        List<AdminPaymentResponse> content = result.rows().stream().map(row -> {
            boolean isCompleted = "COMPLETED".equalsIgnoreCase(row.status());
            Double totalCost = row.totalCost() != null ? row.totalCost() : 0.0;
            Double advanceAmount = row.advanceAmount() != null ? row.advanceAmount() : totalCost * 0.20;
            Double balanceAmount = row.balanceAmount() != null ? row.balanceAmount() : totalCost * 0.80;

            Double paidAmount = isCompleted ? totalCost : advanceAmount;
            Double remainingBalance = isCompleted ? 0.0 : balanceAmount;
            String paymentCompletion = isCompleted ? "100%" : "20%";
            String compStatus = isCompleted ? "FULL_100" : "PARTIAL_20";

            LocalDate dueDate = row.endDate();
            boolean isOverdue = !isCompleted && dueDate != null && dueDate.isBefore(today);

            return AdminPaymentResponse.builder()
                    .bookingId(row.id())
                    .bookingType(row.type())
                    .bookingStatus(row.status())
                    .customerId(row.customerId())
                    .customerName(row.customerName())
                    .customerEmail(row.customerEmail())
                    .customerPhone(row.customerPhone())
                    .providerId(row.providerId())
                    .providerName(row.providerName())
                    .tripId(row.tripId())
                    .tripTitle(row.tripTitle())
                    .startDate(row.startDate())
                    .endDate(row.endDate())
                    .paymentDueDate(dueDate)
                    .totalCost(totalCost)
                    .advanceAmount(advanceAmount)
                    .balanceAmount(balanceAmount)
                    .paidAmount(paidAmount)
                    .remainingBalance(remainingBalance)
                    .currency("USD")
                    .paymentCompletion(paymentCompletion)
                    .completionStatus(compStatus)
                    .initialPaymentDate(row.initialPaidAt() != null ? row.initialPaidAt() : row.createdAt())
                    .finalPaymentDate(row.finalPaidAt())
                    .isOverdue(isOverdue)
                    .createdAt(row.createdAt())
                    .build();
        }).toList();

        int totalPages = (int) Math.ceil((double) result.totalElements() / size);
        return PageResponse.<AdminPaymentResponse>builder()
                .content(content)
                .totalElements(result.totalElements())
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .build();
    }

    public AdminPaymentSummaryResponse getPaymentSummary() {
        return adminPaymentQueryRepository.getSummary(LocalDate.now());
    }

    public AdminPaymentDetailResponse getPaymentDetail(String type, Long bookingId) {
        LocalDate today = LocalDate.now();
        if ("VEHICLE".equalsIgnoreCase(type)) {
            VehicleBooking vb = vehicleBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new com.exploreceylon.backend.exception.ResourceNotFoundException("Vehicle booking not found: " + bookingId));

            List<VehiclePayment> payments = vehiclePaymentRepository.findByVehicleBookingId(bookingId);
            VehiclePayment advancePayment = payments.stream()
                    .filter(p -> p.getPaymentPhase() == VehiclePayment.PaymentPhase.ADVANCE && p.getStatus() == VehiclePayment.PaymentStatus.COMPLETED)
                    .findFirst().orElse(null);
            VehiclePayment finalPayment = payments.stream()
                    .filter(p -> p.getPaymentPhase() == VehiclePayment.PaymentPhase.FINAL && p.getStatus() == VehiclePayment.PaymentStatus.COMPLETED)
                    .findFirst().orElse(null);

            boolean isCompleted = vb.getStatus() == VehicleBooking.BookingStatus.COMPLETED;
            Double totalCost = vb.getTotalCost() != null ? vb.getTotalCost() : 0.0;
            Double advanceAmt = vb.getAdvanceAmount() != null ? vb.getAdvanceAmount() : totalCost * 0.20;
            Double balanceAmt = vb.getBalanceAmount() != null ? vb.getBalanceAmount() : totalCost * 0.80;
            Double totalPaid = isCompleted ? totalCost : (advancePayment != null ? advancePayment.getAmount() : advanceAmt);
            Double remainingAmt = isCompleted ? 0.0 : balanceAmt;
            boolean isOverdue = !isCompleted && vb.getDropoffDate() != null && vb.getDropoffDate().isBefore(today);
            long daysOverdue = isOverdue ? java.time.temporal.ChronoUnit.DAYS.between(vb.getDropoffDate(), today) : 0L;

            Map<String, Object> providerDetails = new HashMap<>();
            Vehicle v = vb.getVehicle();
            if (v != null) {
                providerDetails.put("vehicleType", v.getType() != null ? v.getType().name() : "N/A");
                providerDetails.put("brand", v.getBrand());
                providerDetails.put("model", v.getModel());
                providerDetails.put("licensePlate", v.getLicensePlate());
                providerDetails.put("seats", v.getSeats());
                providerDetails.put("driverName", v.getDriverName());
                providerDetails.put("driverPhone", v.getDriverPhone());
                providerDetails.put("whatsappNumber", v.getWhatsappNumber());
                providerDetails.put("driverLanguages", v.getDriverLanguages());
            }

            AdminPaymentDetailResponse.PhaseDetail initPhase = AdminPaymentDetailResponse.PhaseDetail.builder()
                    .phase("ADVANCE")
                    .percent(20)
                    .amount(advanceAmt)
                    .status(advancePayment != null ? advancePayment.getStatus().name() : (vb.getStatus() != VehicleBooking.BookingStatus.PENDING_PAYMENT ? "COMPLETED" : "PENDING"))
                    .payhereOrderId(advancePayment != null ? advancePayment.getPayhereOrderId() : null)
                    .payherePaymentId(advancePayment != null ? advancePayment.getPayherePaymentId() : null)
                    .paidAt(advancePayment != null ? advancePayment.getPaidAt() : vb.getCreatedAt())
                    .currency("USD")
                    .dueDate(vb.getPickupDate())
                    .build();

            AdminPaymentDetailResponse.PhaseDetail finalPhase = AdminPaymentDetailResponse.PhaseDetail.builder()
                    .phase("FINAL")
                    .percent(80)
                    .amount(balanceAmt)
                    .status(finalPayment != null ? finalPayment.getStatus().name() : (isCompleted ? "COMPLETED" : "PENDING"))
                    .payhereOrderId(finalPayment != null ? finalPayment.getPayhereOrderId() : null)
                    .payherePaymentId(finalPayment != null ? finalPayment.getPayherePaymentId() : null)
                    .paidAt(finalPayment != null ? finalPayment.getPaidAt() : null)
                    .currency("USD")
                    .dueDate(vb.getDropoffDate())
                    .build();

            LocalDateTime lastReminder = notificationService.getLastReminderSentAt("VEHICLE", bookingId);

            return AdminPaymentDetailResponse.builder()
                    .bookingId(vb.getId())
                    .bookingType("VEHICLE")
                    .bookingStatus(vb.getStatus().name())
                    .bookingCreatedAt(vb.getCreatedAt())
                    .startDate(vb.getPickupDate())
                    .endDate(vb.getDropoffDate())
                    .pickupTime(vb.getPickupTime())
                    .dropoffTime(vb.getDropoffTime())
                    .pickupLocation(vb.getPickupLocation())
                    .dropoffLocation(vb.getDropoffLocation())
                    .notes(vb.getNotes())
                    .customerId(vb.getUser() != null ? vb.getUser().getId() : null)
                    .customerName(vb.getUser() != null ? vb.getUser().getName() : "N/A")
                    .customerEmail(vb.getUser() != null ? vb.getUser().getEmail() : "N/A")
                    .customerPhone(vb.getUser() != null ? vb.getUser().getPhone() : "N/A")
                    .providerId(v != null ? v.getId() : null)
                    .providerName(v != null ? v.getName() : "N/A")
                    .providerPhone(v != null ? v.getDriverPhone() : "N/A")
                    .providerDistrict(v != null ? v.getDistrict() : "N/A")
                    .pricePerDay(v != null ? v.getPricePerDay() : null)
                    .providerDetails(providerDetails)
                    .totalCost(totalCost)
                    .advanceAmount(advanceAmt)
                    .balanceAmount(balanceAmt)
                    .totalPaid(totalPaid)
                    .remainingBalance(remainingAmt)
                    .currency("USD")
                    .paymentCompletion(isCompleted ? "100%" : "20%")
                    .completionStatus(isCompleted ? "FULL_100" : "PARTIAL_20")
                    .initialPayment(initPhase)
                    .finalPayment(finalPhase)
                    .isOverdue(isOverdue)
                    .paymentDueDate(vb.getDropoffDate())
                    .daysOverdue(daysOverdue)
                    .tripId(vb.getTrip() != null ? vb.getTrip().getId() : null)
                    .tripTitle(vb.getTrip() != null ? vb.getTrip().getTitle() : null)
                    .reminderSent(lastReminder != null)
                    .lastReminderSentAt(lastReminder)
                    .build();

        } else if ("GUIDE".equalsIgnoreCase(type)) {
            GuideBooking gb = guideBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new com.exploreceylon.backend.exception.ResourceNotFoundException("Guide booking not found: " + bookingId));

            List<GuidePayment> payments = guidePaymentRepository.findByGuideBookingId(bookingId);
            GuidePayment advancePayment = payments.stream()
                    .filter(p -> p.getPaymentPhase() == GuidePayment.PaymentPhase.ADVANCE && p.getStatus() == GuidePayment.PaymentStatus.COMPLETED)
                    .findFirst().orElse(null);
            GuidePayment finalPayment = payments.stream()
                    .filter(p -> p.getPaymentPhase() == GuidePayment.PaymentPhase.FINAL && p.getStatus() == GuidePayment.PaymentStatus.COMPLETED)
                    .findFirst().orElse(null);

            boolean isCompleted = gb.getStatus() == GuideBooking.BookingStatus.COMPLETED;
            Double totalCost = gb.getTotalCost() != null ? gb.getTotalCost() : 0.0;
            Double advanceAmt = gb.getAdvanceAmount() != null ? gb.getAdvanceAmount() : totalCost * 0.20;
            Double balanceAmt = gb.getBalanceAmount() != null ? gb.getBalanceAmount() : totalCost * 0.80;
            Double totalPaid = isCompleted ? totalCost : (advancePayment != null ? advancePayment.getAmount() : advanceAmt);
            Double remainingAmt = isCompleted ? 0.0 : balanceAmt;
            boolean isOverdue = !isCompleted && gb.getEndDate() != null && gb.getEndDate().isBefore(today);
            long daysOverdue = isOverdue ? java.time.temporal.ChronoUnit.DAYS.between(gb.getEndDate(), today) : 0L;

            Map<String, Object> providerDetails = new HashMap<>();
            TourGuide g = gb.getGuide();
            if (g != null) {
                providerDetails.put("languages", g.getLanguages());
                providerDetails.put("specialties", g.getSpecialties());
                providerDetails.put("district", g.getDistrict());
                providerDetails.put("rating", g.getRating());
                providerDetails.put("phone", g.getPhone());
                providerDetails.put("whatsappNumber", g.getWhatsappNumber());
            }

            AdminPaymentDetailResponse.PhaseDetail initPhase = AdminPaymentDetailResponse.PhaseDetail.builder()
                    .phase("ADVANCE")
                    .percent(20)
                    .amount(advanceAmt)
                    .status(advancePayment != null ? advancePayment.getStatus().name() : (gb.getStatus() != GuideBooking.BookingStatus.PENDING_PAYMENT ? "COMPLETED" : "PENDING"))
                    .payhereOrderId(advancePayment != null ? advancePayment.getPayhereOrderId() : null)
                    .payherePaymentId(advancePayment != null ? advancePayment.getPayherePaymentId() : null)
                    .paidAt(advancePayment != null ? advancePayment.getPaidAt() : gb.getCreatedAt())
                    .currency("USD")
                    .dueDate(gb.getStartDate())
                    .build();

            AdminPaymentDetailResponse.PhaseDetail finalPhase = AdminPaymentDetailResponse.PhaseDetail.builder()
                    .phase("FINAL")
                    .percent(80)
                    .amount(balanceAmt)
                    .status(finalPayment != null ? finalPayment.getStatus().name() : (isCompleted ? "COMPLETED" : "PENDING"))
                    .payhereOrderId(finalPayment != null ? finalPayment.getPayhereOrderId() : null)
                    .payherePaymentId(finalPayment != null ? finalPayment.getPayherePaymentId() : null)
                    .paidAt(finalPayment != null ? finalPayment.getPaidAt() : null)
                    .currency("USD")
                    .dueDate(gb.getEndDate())
                    .build();

            LocalDateTime lastReminder = notificationService.getLastReminderSentAt("GUIDE", bookingId);

            return AdminPaymentDetailResponse.builder()
                    .bookingId(gb.getId())
                    .bookingType("GUIDE")
                    .bookingStatus(gb.getStatus().name())
                    .bookingCreatedAt(gb.getCreatedAt())
                    .startDate(gb.getStartDate())
                    .endDate(gb.getEndDate())
                    .notes(gb.getNotes())
                    .customerId(gb.getUser() != null ? gb.getUser().getId() : null)
                    .customerName(gb.getUser() != null ? gb.getUser().getName() : "N/A")
                    .customerEmail(gb.getUser() != null ? gb.getUser().getEmail() : "N/A")
                    .customerPhone(gb.getUser() != null ? gb.getUser().getPhone() : "N/A")
                    .providerId(g != null ? g.getId() : null)
                    .providerName(g != null ? g.getFullName() : "N/A")
                    .providerPhone(g != null ? g.getPhone() : "N/A")
                    .providerDistrict(g != null ? g.getDistrict() : "N/A")
                    .pricePerDay(g != null ? g.getPricePerDay() : null)
                    .providerDetails(providerDetails)
                    .totalCost(totalCost)
                    .advanceAmount(advanceAmt)
                    .balanceAmount(balanceAmt)
                    .totalPaid(totalPaid)
                    .remainingBalance(remainingAmt)
                    .currency("USD")
                    .paymentCompletion(isCompleted ? "100%" : "20%")
                    .completionStatus(isCompleted ? "FULL_100" : "PARTIAL_20")
                    .initialPayment(initPhase)
                    .finalPayment(finalPhase)
                    .isOverdue(isOverdue)
                    .paymentDueDate(gb.getEndDate())
                    .daysOverdue(daysOverdue)
                    .tripId(gb.getTrip() != null ? gb.getTrip().getId() : null)
                    .tripTitle(gb.getTrip() != null ? gb.getTrip().getTitle() : null)
                    .reminderSent(lastReminder != null)
                    .lastReminderSentAt(lastReminder)
                    .build();
        } else {
            throw new RuntimeException("Invalid booking type: " + type + ". Expected VEHICLE or GUIDE.");
        }
    }

    public Map<String, Object> notifyOverdueUser(String type, Long bookingId) {
        return notifyOverdueUser(type, bookingId, null);
    }

    public Map<String, Object> notifyOverdueUser(String type, Long bookingId, String customMessage) {
        LocalDate today = LocalDate.now();
        User user;
        Double balanceAmount;
        LocalDate serviceEndDate;
        String providerName;

        if ("VEHICLE".equalsIgnoreCase(type)) {
            VehicleBooking vb = vehicleBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new com.exploreceylon.backend.exception.ResourceNotFoundException("Vehicle booking not found: " + bookingId));
            if (vb.getStatus() != VehicleBooking.BookingStatus.CONFIRMED) {
                throw new RuntimeException("Booking #" + bookingId + " is not in CONFIRMED state (status=" + vb.getStatus() + "). Only confirmed bookings with pending balance can receive reminders.");
            }
            balanceAmount = vb.getBalanceAmount() != null ? vb.getBalanceAmount() : vb.getTotalCost() * 0.80;
            if (balanceAmount <= 0) {
                throw new RuntimeException("Booking #" + bookingId + " has no remaining balance.");
            }
            user = vb.getUser();
            serviceEndDate = vb.getDropoffDate();
            providerName = vb.getVehicle() != null ? vb.getVehicle().getName() : "Vehicle Rental";
        } else if ("GUIDE".equalsIgnoreCase(type)) {
            GuideBooking gb = guideBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new com.exploreceylon.backend.exception.ResourceNotFoundException("Guide booking not found: " + bookingId));
            if (gb.getStatus() != GuideBooking.BookingStatus.CONFIRMED) {
                throw new RuntimeException("Booking #" + bookingId + " is not in CONFIRMED state (status=" + gb.getStatus() + "). Only confirmed bookings with pending balance can receive reminders.");
            }
            balanceAmount = gb.getBalanceAmount() != null ? gb.getBalanceAmount() : gb.getTotalCost() * 0.80;
            if (balanceAmount <= 0) {
                throw new RuntimeException("Booking #" + bookingId + " has no remaining balance.");
            }
            user = gb.getUser();
            serviceEndDate = gb.getEndDate();
            providerName = gb.getGuide() != null ? gb.getGuide().getFullName() : "Tour Guide";
        } else {
            throw new RuntimeException("Invalid booking type: " + type + ". Expected VEHICLE or GUIDE.");
        }

        if (user == null) {
            throw new RuntimeException("No customer account linked to booking #" + bookingId);
        }

        String finalMessage;
        if (customMessage != null) {
            String trimmed = customMessage.trim();
            if (trimmed.isEmpty()) {
                throw new RuntimeException("Notification message cannot be blank");
            }
            if (trimmed.length() > 500) {
                throw new RuntimeException("Notification message exceeds maximum allowed length of 500 characters");
            }
            // Strip any raw HTML tags for security
            finalMessage = trimmed.replaceAll("<[^>]*>", "");
        } else {
            boolean isOverdue = serviceEndDate != null && serviceEndDate.isBefore(today);
            if (isOverdue) {
                finalMessage = String.format("Your service for booking #%d ended on %s. The remaining 80%% balance of $%.2f is now overdue. Please complete your payment to finalize this booking.",
                        bookingId, serviceEndDate, balanceAmount);
            } else {
                finalMessage = String.format("Your remaining 80%% payment of $%.2f for booking #%d is still pending. Please complete the outstanding payment to settle your booking.",
                        balanceAmount, bookingId);
            }
        }

        boolean isOverdue = serviceEndDate != null && serviceEndDate.isBefore(today);
        String title = isOverdue
                ? "Overdue 80% Balance Payment — " + providerName
                : "Payment Reminder — " + providerName;

        boolean sent = notificationService.sendAdminOverdueReminder(user, type.toUpperCase(), bookingId, title, finalMessage);
        if (!sent) {
            return Map.of(
                    "success", false,
                    "alreadySent", true,
                    "message", "A balance reminder notification was already sent to this customer within the last 12 hours."
            );
        }

        return Map.of(
                "success", true,
                "alreadySent", false,
                "message", "Payment reminder successfully sent to " + user.getEmail()
        );
    }
}
