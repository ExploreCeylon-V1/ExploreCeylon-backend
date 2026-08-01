package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.budget.*;
import com.exploreceylon.backend.model.BudgetItem;
import com.exploreceylon.backend.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    // POST /api/v1/budget
    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            @Valid @RequestBody CreateBudgetRequest request) {
        return ResponseEntity.ok(
                budgetService.createBudget(request));
    }

    // GET /api/v1/budget/trip/{tripId}
    @GetMapping("/trip/{tripId}")
    public ResponseEntity<BudgetResponse> getBudgetByTrip(
            @PathVariable Long tripId) {
        return ResponseEntity.ok(
                budgetService.getBudgetByTrip(tripId));
    }

    // GET /api/v1/budget/trip/{tripId}/summary
    @GetMapping("/trip/{tripId}/summary")
    public ResponseEntity<BudgetSummaryResponse> getSummary(
            @PathVariable Long tripId,
            @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(
                budgetService.getSummary(tripId, currency));
    }

    // PUT /api/v1/budget/{budgetId}
    @PutMapping("/{budgetId}")
    public ResponseEntity<BudgetResponse> updateBudget(
            @PathVariable Long budgetId,
            @RequestParam(required = false) Double totalBudget,
            @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(
                budgetService.updateBudget(
                        budgetId, totalBudget, currency));
    }

    // PUT /api/v1/budget/{budgetId}/categories
    // Body: {"HOTEL": 350.0, "FOOD": 70.0, ...}
    @PutMapping("/{budgetId}/categories")
    public ResponseEntity<BudgetResponse> updateCategoryBudgets(
            @PathVariable Long budgetId,
            @RequestBody java.util.Map<String, Double> allocations) {
        return ResponseEntity.ok(
                budgetService.updateCategoryBudgets(budgetId, allocations));
    }

    // POST /api/v1/budget/{budgetId}/repair-dates
    // One-time fix for auto-added items whose date predates the fix that
    // made autoAddFromBooking use the booking's real date.
    @PostMapping("/{budgetId}/repair-dates")
    public ResponseEntity<BudgetResponse> repairAutoAddedDates(
            @PathVariable Long budgetId) {
        return ResponseEntity.ok(
                budgetService.repairAutoAddedDates(budgetId));
    }

    // POST /api/v1/budget/{budgetId}/items
    @PostMapping("/{budgetId}/items")
    public ResponseEntity<BudgetItemResponse> addItem(
            @PathVariable Long budgetId,
            @Valid @RequestBody AddBudgetItemRequest request) {
        return ResponseEntity.ok(
                budgetService.addItem(budgetId, request));
    }

    // GET /api/v1/budget/{budgetId}/items
    @GetMapping("/{budgetId}/items")
    public ResponseEntity<List<BudgetItemResponse>> getItems(
            @PathVariable Long budgetId,
            @RequestParam(required = false)
            BudgetItem.ItemCategory category) {
        return ResponseEntity.ok(
                budgetService.getItems(budgetId, category));
    }

    // PUT /api/v1/budget/{budgetId}/items/{itemId}
    @PutMapping("/{budgetId}/items/{itemId}")
    public ResponseEntity<BudgetItemResponse> updateItem(
            @PathVariable Long budgetId,
            @PathVariable Long itemId,
            @RequestBody AddBudgetItemRequest request) {
        return ResponseEntity.ok(
                budgetService.updateItem(budgetId, itemId, request));
    }

    // DELETE /api/v1/budget/{budgetId}/items/{itemId}
    @DeleteMapping("/{budgetId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long budgetId,
            @PathVariable Long itemId) {
        budgetService.deleteItem(budgetId, itemId);
        return ResponseEntity.ok().build();
    }
}
