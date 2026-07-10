package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.admin.AdminReviewResponse;
import com.exploreceylon.backend.dto.admin.BulkReviewDeleteRequest;
import com.exploreceylon.backend.dto.admin.PageResponse;
import com.exploreceylon.backend.service.AdminReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// All routes here fall under SecurityConfig's "/api/v1/admin/**" ->
// hasRole("ADMIN") rule — no separate security matcher needed.
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    // GET /api/v1/admin/reviews
    @GetMapping
    public ResponseEntity<PageResponse<AdminReviewResponse>> getAllReviews(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(adminReviewService.getAllReviews(
                entityType, search, rating, dateFrom, dateTo,
                sortBy, sortDir, page, size));
    }

    // DELETE /api/v1/admin/reviews/{entityType}/{id}
    @DeleteMapping("/{entityType}/{id}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable String entityType,
            @PathVariable Long id) {
        adminReviewService.deleteReview(entityType, id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/v1/admin/reviews/bulk-delete
    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDelete(@Valid @RequestBody BulkReviewDeleteRequest request) {
        int count = adminReviewService.bulkDelete(request.getItems());
        return ResponseEntity.ok(java.util.Map.of("deleted", count));
    }
}
