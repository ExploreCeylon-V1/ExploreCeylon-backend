package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.maintenance.MaintenanceStatusResponse;
import com.exploreceylon.backend.dto.maintenance.UpdateMaintenanceRequest;
import com.exploreceylon.backend.model.MaintenanceMode;
import com.exploreceylon.backend.repository.MaintenanceModeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceService {

    // Singleton row — there is only ever one maintenance state, so it's
    // always stored/read at this fixed id rather than a full CRUD table.
    private static final Long SINGLETON_ID = 1L;

    private final MaintenanceModeRepository maintenanceModeRepository;

    // ── Get Status (public) ────────────────────────────────
    public MaintenanceStatusResponse getStatus() {
        return maintenanceModeRepository.findById(SINGLETON_ID)
                .map(this::toResponse)
                .orElseGet(() -> {
                    // No row yet — maintenance mode has never been configured.
                    MaintenanceStatusResponse res = new MaintenanceStatusResponse();
                    res.setActive(false);
                    res.setTitle("We'll be back soon");
                    res.setDescription(
                            "ExploreCeylon is currently undergoing scheduled maintenance. Please check back shortly.");
                    return res;
                });
    }

    // ── Update Status (Admin) ──────────────────────────────
    public MaintenanceStatusResponse updateStatus(UpdateMaintenanceRequest req) {
        MaintenanceMode mode = maintenanceModeRepository.findById(SINGLETON_ID)
                .orElseGet(() -> MaintenanceMode.builder().id(SINGLETON_ID).build());

        mode.setActive(req.getActive());
        mode.setTitle(req.getTitle());
        mode.setDescription(req.getDescription());

        MaintenanceMode saved = maintenanceModeRepository.save(mode);
        log.info("Maintenance mode set to active={}", saved.getActive());
        return toResponse(saved);
    }

    // ── MAPPER ─────────────────────────────────────────────
    private MaintenanceStatusResponse toResponse(MaintenanceMode mode) {
        MaintenanceStatusResponse res = new MaintenanceStatusResponse();
        res.setActive(mode.getActive());
        res.setTitle(mode.getTitle());
        res.setDescription(mode.getDescription());
        return res;
    }
}
