package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.maintenance.MaintenanceStatusResponse;
import com.exploreceylon.backend.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Public — see SecurityConfig's explicit permitAll for GET /api/v1/maintenance/status.
// The traveler frontend must be able to check this before a user is logged in.
@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    // GET /api/v1/maintenance/status
    @GetMapping("/status")
    public ResponseEntity<MaintenanceStatusResponse> getStatus() {
        return ResponseEntity.ok(maintenanceService.getStatus());
    }
}
