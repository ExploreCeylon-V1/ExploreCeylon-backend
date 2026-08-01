package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.maintenance.MaintenanceStatusResponse;
import com.exploreceylon.backend.dto.maintenance.UpdateMaintenanceRequest;
import com.exploreceylon.backend.service.MaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Falls under SecurityConfig's "/api/v1/admin/**" -> hasRole("ADMIN") rule.
@RestController
@RequestMapping("/api/v1/admin/maintenance")
@RequiredArgsConstructor
public class AdminMaintenanceController {

    private final MaintenanceService maintenanceService;

    // PUT /api/v1/admin/maintenance
    @PutMapping
    public ResponseEntity<MaintenanceStatusResponse> updateStatus(
            @Valid @RequestBody UpdateMaintenanceRequest request) {
        return ResponseEntity.ok(maintenanceService.updateStatus(request));
    }
}
