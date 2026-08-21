package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.admin.*;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // GET /api/v1/admin/dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // GET /api/v1/admin/dashboard/recent-activity
    @GetMapping("/dashboard/recent-activity")
    public ResponseEntity<RecentActivityResponse> getRecentActivity() {
        return ResponseEntity.ok(adminService.getRecentActivity());
    }

    // GET /api/v1/admin/dashboard/top-lists
    @GetMapping("/dashboard/top-lists")
    public ResponseEntity<TopListsResponse> getTopLists() {
        return ResponseEntity.ok(adminService.getTopLists());
    }

    // GET /api/v1/admin/bookings
    @GetMapping("/bookings")
    public ResponseEntity<PageResponse<AdminBookingResponse>> getAllBookings(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAllBookings(
                type, status, search, customer, provider,
                dateFrom, dateTo, sortBy, sortDir, page, size));
    }

    // POST /api/v1/admin/bookings/bulk-status
    @PostMapping("/bookings/bulk-status")
    public ResponseEntity<?> bulkUpdateBookingStatus(@Valid @RequestBody BulkBookingStatusRequest request) {
        int count = adminService.bulkUpdateBookingStatus(request.getItems(), request.getStatus());
        return ResponseEntity.ok(java.util.Map.of("updated", count));
    }

    // ── Payment Management ────────────────────────────────

    // GET /api/v1/admin/payments
    @GetMapping("/payments")
    public ResponseEntity<PageResponse<AdminPaymentResponse>> getAllPayments(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String completionStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAllPayments(
                type, completionStatus, search, dateFrom, dateTo, sortBy, sortDir, page, size));
    }

    // GET /api/v1/admin/payments/summary
    @GetMapping("/payments/summary")
    public ResponseEntity<AdminPaymentSummaryResponse> getPaymentSummary() {
        return ResponseEntity.ok(adminService.getPaymentSummary());
    }

    // GET /api/v1/admin/payments/{type}/{bookingId}
    @GetMapping("/payments/{type}/{bookingId}")
    public ResponseEntity<AdminPaymentDetailResponse> getPaymentDetail(
            @PathVariable String type,
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(adminService.getPaymentDetail(type, bookingId));
    }

    // POST /api/v1/admin/payments/{type}/{bookingId}/notify
    @PostMapping("/payments/{type}/{bookingId}/notify")
    public ResponseEntity<java.util.Map<String, Object>> notifyOverdueUser(
            @PathVariable String type,
            @PathVariable Long bookingId,
            @jakarta.validation.Valid @RequestBody(required = false) AdminPaymentNotificationRequest request) {
        String customMessage = request != null ? request.getMessage() : null;
        return ResponseEntity.ok(adminService.notifyOverdueUser(type, bookingId, customMessage));
    }

    // GET /api/v1/admin/revenue
    @GetMapping("/revenue")
    public ResponseEntity<RevenueResponse> getRevenue(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(
                adminService.getRevenueSummary(period));
    }

    // GET /api/v1/admin/users
    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean emailVerified,
            @RequestParam(required = false) Boolean phoneVerified,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAllUsers(
                search, role, active, emailVerified, phoneVerified,
                sortBy, sortDir, page, size));
    }

    // GET /api/v1/admin/users/{id}
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    // PUT /api/v1/admin/users/{id}/activate
    @PutMapping("/users/{id}/activate")
    public ResponseEntity<?> activateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody AdminPasswordConfirmRequest request) {
        adminService.activateUser(id, admin, request.getPassword());
        return ResponseEntity.ok().build();
    }

    // PUT /api/v1/admin/users/{id}/deactivate
    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<?> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody AdminPasswordConfirmRequest request) {
        adminService.deactivateUser(id, admin, request.getPassword());
        return ResponseEntity.ok().build();
    }

    // POST /api/v1/admin/users/bulk-activate
    @PostMapping("/users/bulk-activate")
    public ResponseEntity<?> bulkActivateUsers(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody BulkIdsRequest request) {
        int count = adminService.bulkSetUserActive(request.getIds(), true, admin, request.getPassword());
        return ResponseEntity.ok(java.util.Map.of("updated", count));
    }

    // POST /api/v1/admin/users/bulk-deactivate
    @PostMapping("/users/bulk-deactivate")
    public ResponseEntity<?> bulkDeactivateUsers(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody BulkIdsRequest request) {
        int count = adminService.bulkSetUserActive(request.getIds(), false, admin, request.getPassword());
        return ResponseEntity.ok(java.util.Map.of("updated", count));
    }

    // PUT /api/v1/admin/users/{id}/role
    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> changeUserRole(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(adminService.changeUserRole(id, request.getRole(), admin, request.getPassword()));
    }

    // PUT /api/v1/admin/users/{id}/reset-verification?type=EMAIL|PHONE|BOTH
    @PutMapping("/users/{id}/reset-verification")
    public ResponseEntity<UserResponse> resetVerification(
            @PathVariable Long id,
            @RequestParam(defaultValue = "BOTH") String type) {
        return ResponseEntity.ok(adminService.resetVerification(id, type));
    }

    // GET /api/v1/admin/stats/vehicles
    @GetMapping("/stats/vehicles")
    public ResponseEntity<?> getVehicleStats() {
        return ResponseEntity.ok(adminService.getVehicleStats());
    }

    // GET /api/v1/admin/stats/guides
    @GetMapping("/stats/guides")
    public ResponseEntity<?> getGuideStats() {
        return ResponseEntity.ok(adminService.getGuideStats());
    }
}