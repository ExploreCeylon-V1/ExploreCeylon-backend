package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.admin.*;
import com.exploreceylon.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // GET /api/v1/admin/bookings
    @GetMapping("/bookings")
    public ResponseEntity<AllBookingsResponse> getAllBookings(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(
                adminService.getAllBookings(type, status));
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
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // PUT /api/v1/admin/users/{id}/deactivate
    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<?> deactivateUser(
            @PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.ok().build();
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