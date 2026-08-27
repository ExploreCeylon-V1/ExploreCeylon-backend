package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.budget.*;
import com.exploreceylon.backend.dto.trip.SyncableBookingResponse;
import com.exploreceylon.backend.exception.UnauthenticatedException;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BudgetService {

    private final BudgetRepository        budgetRepository;
    private final BudgetItemRepository    itemRepository;
    private final UserRepository          userRepository;
    private final TripRepository          tripRepository;
    private final VehicleBookingRepository vehicleBookingRepository;
    private final GuideBookingRepository   guideBookingRepository;

    // Currency conversion rates (relative to USD)
    private static final Map<String, Double> RATES = Map.of(
            "USD", 1.0,
            "LKR", 325.0,
            "EUR", 0.92,
            "GBP", 0.79
    );

    // ── Current User ───────────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthenticatedException("User not found"));
    }

    // ── Create Budget ──────────────────────────────────────
    public BudgetResponse createBudget(CreateBudgetRequest req) {
        User user = getCurrentUser();

        if (budgetRepository.existsByTripId(req.getTripId())) {
            throw new RuntimeException(
                    "Budget already exists for trip: "
                            + req.getTripId());
        }

        Trip trip = tripRepository.findById(req.getTripId())
                .orElseThrow(() -> new RuntimeException(
                        "Trip not found: " + req.getTripId()));

        Budget budget = Budget.builder()
                .trip(trip)
                .user(user)
                .totalBudget(req.getTotalBudget())
                .currency(req.getCurrency() != null
                        ? req.getCurrency() : "USD")
                .build();

        Budget saved = budgetRepository.save(budget);
        log.info("Budget created for trip: {}", req.getTripId());
        return toResponse(saved);
    }

    // ── Get Budget by Trip ─────────────────────────────────
    public BudgetResponse getBudgetByTrip(Long tripId) {
        Budget budget = budgetRepository.findByTripId(tripId)
                .orElseThrow(() -> new RuntimeException(
                        "Budget not found for trip: " + tripId));
        return toResponse(budget);
    }

    // ── Update Total Budget ────────────────────────────────
    public BudgetResponse updateBudget(Long budgetId,
                                        Double newTotal,
                                        String currency) {
        Budget budget = findBudget(budgetId);
        if (newTotal   != null) budget.setTotalBudget(newTotal);
        if (currency   != null) budget.setCurrency(currency);
        return toResponse(budgetRepository.save(budget));
    }

    // ── Set Per-Category Allocations ───────────────────────
    public BudgetResponse updateCategoryBudgets(
            Long budgetId, Map<String, Double> allocations) {
        Budget budget = findBudget(budgetId);

        Map<BudgetItem.ItemCategory, Double> parsed = new HashMap<>();
        for (Map.Entry<String, Double> e : allocations.entrySet()) {
            BudgetItem.ItemCategory category;
            try {
                category = BudgetItem.ItemCategory
                        .valueOf(e.getKey().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException(
                        "Unknown budget category: " + e.getKey());
            }
            if (e.getValue() == null || e.getValue() < 0) {
                throw new RuntimeException(
                        "Invalid amount for category: " + e.getKey());
            }
            parsed.put(category, e.getValue());
        }

        budget.getCategoryBudgets().clear();
        budget.getCategoryBudgets().putAll(parsed);
        Budget saved = budgetRepository.save(budget);
        log.info("Category budgets updated for budget {}", budgetId);
        return toResponse(saved);
    }

    // ── Add Item Manually ──────────────────────────────────
    public BudgetItemResponse addItem(Long budgetId,
                                       AddBudgetItemRequest req) {
        Budget budget = findBudget(budgetId);

        BudgetItem item = BudgetItem.builder()
                .budget(budget)
                .category(req.getCategory())
                .title(req.getTitle())
                .amount(req.getAmount())
                .currency(req.getCurrency() != null
                        ? req.getCurrency() : budget.getCurrency())
                .date(req.getDate() != null
                        ? req.getDate() : LocalDate.now())
                .notes(req.getNotes())
                .referenceId(req.getReferenceId())
                .autoAdded(false)
                .build();

        BudgetItem saved = itemRepository.save(item);
        log.info("Budget item added: {} — ${}",
                item.getTitle(), item.getAmount());
        return toItemResponse(saved);
    }

    // ── Auto-Add from Booking ──────────────────────────────
    public void autoAddFromBooking(Long tripId,
                                    BudgetItem.ItemCategory category,
                                    String title,
                                    Double amount,
                                    String referenceId,
                                    LocalDate date) {
        Optional<Budget> budgetOpt =
                budgetRepository.findByTripId(tripId);

        if (budgetOpt.isEmpty()) {
            log.warn("No budget found for trip {} — skipping auto-add",
                    tripId);
            return;
        }

        Budget budget = budgetOpt.get();

        // Prevent duplicate
        if (itemRepository.existsByBudgetIdAndReferenceId(
                budget.getId(), referenceId)) {
            log.warn("Duplicate auto-add skipped: {}", referenceId);
            return;
        }

        BudgetItem item = BudgetItem.builder()
                .budget(budget)
                .category(category)
                .title(title)
                .amount(amount)
                .currency(budget.getCurrency())
                // The booking's own date, so the expense lands on the right
                // trip day in the daily spending chart.
                .date(date != null ? date : LocalDate.now())
                .autoAdded(true)
                .referenceId(referenceId)
                .build();

        itemRepository.save(item);
        log.info("Auto-added to budget: {} — ${}",
                title, amount);
    }

    // ── Repair Auto-Added Item Dates ───────────────────────
    // One-time backfill for items created before autoAddFromBooking
    // started recording the booking's real date instead of "today".
    // Re-derives each auto-added item's date from its referenced
    // VehicleBooking ("VB-{id}") or GuideBooking ("GB-{id}").
    public BudgetResponse repairAutoAddedDates(Long budgetId) {
        Budget budget = findBudget(budgetId);
        int repaired = 0;

        for (BudgetItem item : budget.getItems()) {
            if (!Boolean.TRUE.equals(item.getAutoAdded())
                    || item.getReferenceId() == null) {
                continue;
            }
            String ref = item.getReferenceId();
            try {
                if (ref.startsWith("VB-")) {
                    Long bookingId = Long.parseLong(ref.substring(3));
                    vehicleBookingRepository.findById(bookingId)
                            .ifPresent(b -> {
                                if (!b.getPickupDate().equals(item.getDate())) {
                                    item.setDate(b.getPickupDate());
                                    itemRepository.save(item);
                                }
                            });
                    repaired++;
                } else if (ref.startsWith("GB-")) {
                    Long bookingId = Long.parseLong(ref.substring(3));
                    guideBookingRepository.findById(bookingId)
                            .ifPresent(b -> {
                                if (!b.getStartDate().equals(item.getDate())) {
                                    item.setDate(b.getStartDate());
                                    itemRepository.save(item);
                                }
                            });
                    repaired++;
                }
            } catch (NumberFormatException ignored) {
                // referenceId doesn't match the expected "VB-"/"GB-" + id shape
            }
        }

        log.info("Checked {} auto-added items for date repair on budget {}",
                repaired, budgetId);
        return toResponse(findBudget(budgetId));
    }

    // ── Ensure Budget Exists for Trip ───────────────────────
    public Budget ensureBudget(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        User user = getCurrentUser();
        return budgetRepository.findByTripId(tripId).orElseGet(() -> {
            Double defaultBudget = 1000.0;
            if (trip.getBudgetAmountLkr() != null && trip.getBudgetAmountLkr() > 0) {
                defaultBudget = Math.round(trip.getBudgetAmountLkr() / 325.0 * 100.0) / 100.0;
            }
            Budget b = Budget.builder()
                    .trip(trip)
                    .user(trip.getUser() != null ? trip.getUser() : user)
                    .totalBudget(defaultBudget)
                    .currency("USD")
                    .build();
            return budgetRepository.save(b);
        });
    }

    // ── Get Syncable Bookings for Trip ───────────────────────
    public List<SyncableBookingResponse> getSyncableBookings(Long tripId) {
        User user = getCurrentUser();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        if (!trip.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new com.exploreceylon.backend.exception.ForbiddenException("Not your trip");
        }

        Optional<Budget> budgetOpt = budgetRepository.findByTripId(tripId);
        Set<String> syncedRefIds = budgetOpt.map(b -> itemRepository.findByBudgetIdOrderByCreatedAtDesc(b.getId()).stream()
                .map(BudgetItem::getReferenceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
                .orElseGet(HashSet::new);

        String currency = budgetOpt.map(Budget::getCurrency).orElse("USD");

        List<SyncableBookingResponse> result = new ArrayList<>();

        // 1. Vehicle Bookings
        List<VehicleBooking> vehicleBookings = vehicleBookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        for (VehicleBooking vb : vehicleBookings) {
            if (vb.getStatus() != VehicleBooking.BookingStatus.CONFIRMED &&
                vb.getStatus() != VehicleBooking.BookingStatus.COMPLETED) {
                continue;
            }

            Vehicle v = vb.getVehicle();
            String providerName = (v != null)
                    ? (v.getDriverName() != null && !v.getDriverName().isBlank() ? v.getDriverName() + " (" + v.getName() + ")" : v.getName())
                    : "Vehicle Rental";
            String providerImg = (v != null && v.getImageUrls() != null && !v.getImageUrls().isEmpty())
                    ? v.getImageUrls().get(0) : null;

            double totalCost = vb.getTotalCost() != null ? vb.getTotalCost() : 0.0;
            double adv = vb.getAdvanceAmount() != null ? vb.getAdvanceAmount() : totalCost * 0.20;
            double bal = vb.getBalanceAmount() != null ? vb.getBalanceAmount() : totalCost * 0.80;

            boolean isSynced = syncedRefIds.contains("VB-" + vb.getId());

            result.add(SyncableBookingResponse.builder()
                    .bookingId(vb.getId())
                    .bookingType("VEHICLE")
                    .referenceId("VB-" + vb.getId())
                    .providerName(providerName)
                    .providerImage(providerImg)
                    .providerPhone(v != null ? v.getDriverPhone() : null)
                    .providerEmail(v != null ? v.getEmail() : null)
                    .vehicleNumber(v != null ? v.getLicensePlate() : null)
                    .startDate(vb.getPickupDate())
                    .endDate(vb.getDropoffDate())
                    .totalCost(totalCost)
                    .advanceAmount(adv)
                    .balanceAmount(bal)
                    .currency(currency)
                    .status(vb.getStatus().name())
                    .isSynced(isSynced)
                    .tripId(vb.getTrip() != null ? vb.getTrip().getId() : null)
                    .tripTitle(vb.getTrip() != null ? vb.getTrip().getTitle() : null)
                    .pickupLocation(vb.getPickupLocation())
                    .notes(vb.getNotes())
                    .build());
        }

        // 2. Guide Bookings
        List<GuideBooking> guideBookings = guideBookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        for (GuideBooking gb : guideBookings) {
            if (gb.getStatus() != GuideBooking.BookingStatus.CONFIRMED &&
                gb.getStatus() != GuideBooking.BookingStatus.COMPLETED) {
                continue;
            }

            TourGuide g = gb.getGuide();
            String providerName = (g != null) ? g.getFullName() : "Tour Guide";
            String providerImg = (g != null)
                    ? (g.getPhotoUrl() != null ? g.getPhotoUrl() : (g.getImageUrls() != null && !g.getImageUrls().isEmpty() ? g.getImageUrls().get(0) : null))
                    : null;

            double totalCost = gb.getTotalCost() != null ? gb.getTotalCost() : 0.0;
            double adv = gb.getAdvanceAmount() != null ? gb.getAdvanceAmount() : totalCost * 0.20;
            double bal = gb.getBalanceAmount() != null ? gb.getBalanceAmount() : totalCost * 0.80;

            boolean isSynced = syncedRefIds.contains("GB-" + gb.getId());

            result.add(SyncableBookingResponse.builder()
                    .bookingId(gb.getId())
                    .bookingType("GUIDE")
                    .referenceId("GB-" + gb.getId())
                    .providerName(providerName)
                    .providerImage(providerImg)
                    .providerPhone(g != null ? g.getPhone() : null)
                    .providerEmail(g != null ? g.getEmail() : null)
                    .vehicleNumber(null)
                    .startDate(gb.getStartDate())
                    .endDate(gb.getEndDate())
                    .totalCost(totalCost)
                    .advanceAmount(adv)
                    .balanceAmount(bal)
                    .currency(currency)
                    .status(gb.getStatus().name())
                    .isSynced(isSynced)
                    .tripId(gb.getTrip() != null ? gb.getTrip().getId() : null)
                    .tripTitle(gb.getTrip() != null ? gb.getTrip().getTitle() : null)
                    .pickupLocation(null)
                    .notes(gb.getNotes())
                    .build());
        }

        // Sort: items for this trip first, then unsynced first, then by date descending
        result.sort((a, b) -> {
            boolean aThisTrip = Objects.equals(a.getTripId(), tripId);
            boolean bThisTrip = Objects.equals(b.getTripId(), tripId);
            if (aThisTrip != bThisTrip) return aThisTrip ? -1 : 1;
            if (a.isSynced() != b.isSynced()) return a.isSynced() ? 1 : -1;
            if (a.getStartDate() != null && b.getStartDate() != null) {
                return b.getStartDate().compareTo(a.getStartDate());
            }
            return 0;
        });

        return result;
    }

    // ── Sync Booking to Trip Budget ───────────────────────────
    public BudgetResponse syncBookingToTripBudget(Long tripId, String type, Long bookingId) {
        User user = getCurrentUser();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        if (!trip.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new com.exploreceylon.backend.exception.ForbiddenException("Not your trip");
        }

        Budget budget = ensureBudget(tripId);

        if ("VEHICLE".equalsIgnoreCase(type)) {
            VehicleBooking vb = vehicleBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new com.exploreceylon.backend.exception.ResourceNotFoundException("Vehicle booking not found: " + bookingId));

            if (!vb.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
                throw new com.exploreceylon.backend.exception.ForbiddenException("Not your booking");
            }

            if (vb.getStatus() != VehicleBooking.BookingStatus.CONFIRMED &&
                vb.getStatus() != VehicleBooking.BookingStatus.COMPLETED) {
                throw new RuntimeException("Booking is not confirmed yet (status: " + vb.getStatus() + ")");
            }

            String refId = "VB-" + vb.getId();
            if (!itemRepository.existsByBudgetIdAndReferenceId(budget.getId(), refId)) {
                String title = (vb.getVehicle() != null ? vb.getVehicle().getName() : "Vehicle Rental");
                long days = (vb.getPickupDate() != null && vb.getDropoffDate() != null)
                        ? java.time.temporal.ChronoUnit.DAYS.between(vb.getPickupDate(), vb.getDropoffDate()) + 1 : 1;
                if (days > 1) {
                    title += " (" + days + " days)";
                }
                String notes = vb.getVehicle() != null && vb.getVehicle().getDriverName() != null
                        ? "Driver: " + vb.getVehicle().getDriverName()
                        : (vb.getNotes() != null ? vb.getNotes() : "");

                BudgetItem item = BudgetItem.builder()
                        .budget(budget)
                        .category(BudgetItem.ItemCategory.VEHICLE)
                        .title(title)
                        .amount(vb.getTotalCost() != null ? vb.getTotalCost() : 0.0)
                        .currency(budget.getCurrency())
                        .date(vb.getPickupDate() != null ? vb.getPickupDate() : LocalDate.now())
                        .autoAdded(true)
                        .referenceId(refId)
                        .notes(notes)
                        .build();
                itemRepository.save(item);
                log.info("Synced vehicle booking #{} into budget #{} for trip #{}", vb.getId(), budget.getId(), tripId);
            }

            if (vb.getTrip() == null) {
                vb.setTrip(trip);
                vehicleBookingRepository.save(vb);
            }

        } else if ("GUIDE".equalsIgnoreCase(type)) {
            GuideBooking gb = guideBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new com.exploreceylon.backend.exception.ResourceNotFoundException("Guide booking not found: " + bookingId));

            if (!gb.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
                throw new com.exploreceylon.backend.exception.ForbiddenException("Not your booking");
            }

            if (gb.getStatus() != GuideBooking.BookingStatus.CONFIRMED &&
                gb.getStatus() != GuideBooking.BookingStatus.COMPLETED) {
                throw new RuntimeException("Booking is not confirmed yet (status: " + gb.getStatus() + ")");
            }

            String refId = "GB-" + gb.getId();
            if (!itemRepository.existsByBudgetIdAndReferenceId(budget.getId(), refId)) {
                String title = (gb.getGuide() != null ? gb.getGuide().getFullName() : "Tour Guide");
                long days = (gb.getStartDate() != null && gb.getEndDate() != null)
                        ? java.time.temporal.ChronoUnit.DAYS.between(gb.getStartDate(), gb.getEndDate()) + 1 : 1;
                if (days > 1) {
                    title += " (" + days + " days)";
                }
                String notes = gb.getGuide() != null && gb.getGuide().getLanguages() != null
                        ? "Languages: " + gb.getGuide().getLanguages()
                        : (gb.getNotes() != null ? gb.getNotes() : "");

                BudgetItem item = BudgetItem.builder()
                        .budget(budget)
                        .category(BudgetItem.ItemCategory.GUIDE)
                        .title(title)
                        .amount(gb.getTotalCost() != null ? gb.getTotalCost() : 0.0)
                        .currency(budget.getCurrency())
                        .date(gb.getStartDate() != null ? gb.getStartDate() : LocalDate.now())
                        .autoAdded(true)
                        .referenceId(refId)
                        .notes(notes)
                        .build();
                itemRepository.save(item);
                log.info("Synced guide booking #{} into budget #{} for trip #{}", gb.getId(), budget.getId(), tripId);
            }

            if (gb.getTrip() == null) {
                gb.setTrip(trip);
                guideBookingRepository.save(gb);
            }
        } else {
            throw new IllegalArgumentException("Unknown booking type: " + type);
        }

        Budget updatedBudget = budgetRepository.findById(budget.getId())
                .orElse(budget);
        return toResponse(updatedBudget);
    }

    // ── Get Items ──────────────────────────────────────────
    public List<BudgetItemResponse> getItems(
            Long budgetId, BudgetItem.ItemCategory category) {
        List<BudgetItem> items;
        if (category != null) {
            items = itemRepository
                    .findByBudgetIdAndCategoryOrderByCreatedAtDesc(
                            budgetId, category);
        } else {
            items = itemRepository
                    .findByBudgetIdOrderByCreatedAtDesc(budgetId);
        }
        return items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
    }

    // ── Update Item ────────────────────────────────────────
    public BudgetItemResponse updateItem(Long budgetId,
                                          Long itemId,
                                          AddBudgetItemRequest req) {
        BudgetItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException(
                        "Item not found: " + itemId));
        if (req.getCategory() != null) item.setCategory(req.getCategory());
        if (req.getTitle()    != null) item.setTitle(req.getTitle());
        if (req.getAmount()   != null) item.setAmount(req.getAmount());
        if (req.getCurrency() != null) item.setCurrency(req.getCurrency());
        if (req.getDate()     != null) item.setDate(req.getDate());
        if (req.getNotes()    != null) item.setNotes(req.getNotes());
        return toItemResponse(itemRepository.save(item));
    }

    // ── Delete Item ────────────────────────────────────────
    public void deleteItem(Long budgetId, Long itemId) {
        itemRepository.deleteById(itemId);
        log.info("Budget item deleted: {}", itemId);
    }

    // ── Get Summary ────────────────────────────────────────
    public BudgetSummaryResponse getSummary(
            Long tripId, String currency) {

        Budget budget = budgetRepository.findByTripId(tripId)
                .orElseThrow(() -> new RuntimeException(
                        "Budget not found for trip: " + tripId));

        String displayCurrency = currency != null
                ? currency.toUpperCase() : budget.getCurrency();

        double rate = getConversionRate(
                budget.getCurrency(), displayCurrency);

        Double totalSpentRaw = itemRepository
                .getTotalSpent(budget.getId());
        double totalSpent    = totalSpentRaw * rate;
        double totalBudget   = budget.getTotalBudget() * rate;
        double remaining     = totalBudget - totalSpent;
        double usedPct       = totalBudget > 0
                ? (totalSpent / totalBudget) * 100 : 0;

        // Category breakdown
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (BudgetItem.ItemCategory cat
                : BudgetItem.ItemCategory.values()) {
            Double catTotal = itemRepository.getTotalByCategory(
                    budget.getId(), cat);
            if (catTotal != null && catTotal > 0) {
                breakdown.put(cat.name(),
                        Math.round(catTotal * rate * 100.0) / 100.0);
            }
        }

        // Status
        String status;
        if (usedPct >= 100)      status = "OVER_BUDGET";
        else if (usedPct >= 80)  status = "WARNING";
        else                     status = "ON_TRACK";

        return BudgetSummaryResponse.builder()
                .budgetId(budget.getId())
                .tripId(tripId)
                .tripTitle(budget.getTrip().getTitle())
                .totalBudget(Math.round(totalBudget  * 100.0) / 100.0)
                .totalSpent(Math.round(totalSpent    * 100.0) / 100.0)
                .remaining(Math.round(remaining      * 100.0) / 100.0)
                .usedPercentage(Math.round(usedPct   * 10.0)  / 10.0)
                .currency(displayCurrency)
                .status(status)
                .categoryBreakdown(breakdown)
                .build();
    }

    // ── Currency Conversion ────────────────────────────────
    private double getConversionRate(String from, String to) {
        double fromRate = RATES.getOrDefault(from.toUpperCase(), 1.0);
        double toRate   = RATES.getOrDefault(to.toUpperCase(),   1.0);
        return toRate / fromRate;
    }

    // ── Helper ─────────────────────────────────────────────
    private Budget findBudget(Long id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Budget not found: " + id));
    }

    // ── MAPPERS ────────────────────────────────────────────
    private BudgetResponse toResponse(Budget b) {
        BudgetResponse res = new BudgetResponse();
        res.setId(b.getId());
        res.setTripId(b.getTrip().getId());
        res.setTripTitle(b.getTrip().getTitle());
        res.setTotalBudget(b.getTotalBudget());
        res.setCurrency(b.getCurrency());
        res.setCreatedAt(b.getCreatedAt());
        List<BudgetItem> items = itemRepository.findByBudgetIdOrderByCreatedAtDesc(b.getId());
        res.setItems(items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList()));
        Map<String, Double> allocations = new LinkedHashMap<>();
        b.getCategoryBudgets().forEach(
                (cat, amount) -> allocations.put(cat.name(), amount));
        res.setCategoryBudgets(allocations);
        return res;
    }

    private BudgetItemResponse toItemResponse(BudgetItem i) {
        BudgetItemResponse res = new BudgetItemResponse();
        res.setId(i.getId());
        res.setCategory(i.getCategory());
        res.setTitle(i.getTitle());
        res.setAmount(i.getAmount());
        res.setCurrency(i.getCurrency());
        res.setDate(i.getDate());
        res.setAutoAdded(i.getAutoAdded());
        res.setNotes(i.getNotes());
        res.setReferenceId(i.getReferenceId());
        res.setCreatedAt(i.getCreatedAt());
        return res;
    }
}
