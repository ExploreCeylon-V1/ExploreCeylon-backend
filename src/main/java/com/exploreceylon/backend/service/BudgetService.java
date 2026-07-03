package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.budget.*;
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
                .orElseThrow(() -> new RuntimeException(
                        "User not found"));
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
        res.setItems(b.getItems().stream()
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
