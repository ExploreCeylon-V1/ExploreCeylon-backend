package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.SubscribeEmailRequestDTO;
import com.exploreceylon.backend.dto.SubscribeEmailResponseDTO;
import com.exploreceylon.backend.service.SubscribeEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Newsletter Subscription", description = "Endpoints for managing traveler newsletter subscriptions")
public class SubscribeEmailController {

    private final SubscribeEmailService service;

    // ── Public — subscribe ─────────────────────────────────
    // POST /api/subscribe or /api/v1/subscribe
    @Operation(summary = "Public email subscription endpoint")
    @PostMapping({"/api/subscribe", "/api/v1/subscribe"})
    public ResponseEntity<SubscribeEmailResponseDTO> subscribe(
            @Valid @RequestBody SubscribeEmailRequestDTO req) {
        SubscribeEmailResponseDTO response = service.subscribe(req.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Admin — list subscribers ──────────────────────────
    // GET /api/admin/subscribe-emails or /api/v1/admin/subscribe-emails
    @Operation(summary = "Admin list subscribers (all, added, not-added)")
    @GetMapping({"/api/admin/subscribe-emails", "/api/v1/admin/subscribe-emails"})
    public ResponseEntity<List<SubscribeEmailResponseDTO>> getAll(
            @RequestParam(required = false, defaultValue = "all") String status) {
        return ResponseEntity.ok(service.getAll(status));
    }

    // ── Admin — mark as added ─────────────────────────────
    // PATCH /api/admin/subscribe-emails/{id}/mark-added or /api/v1/admin/subscribe-emails/{id}/mark-added
    @Operation(summary = "Admin mark subscriber as added to external email group")
    @PatchMapping({"/api/admin/subscribe-emails/{id}/mark-added", "/api/v1/admin/subscribe-emails/{id}/mark-added"})
    public ResponseEntity<SubscribeEmailResponseDTO> markAsAdded(@PathVariable Long id) {
        return ResponseEntity.ok(service.markAsAdded(id));
    }

    // ── Admin — mark as not-added ─────────────────────────
    // PATCH /api/admin/subscribe-emails/{id}/mark-not-added or /api/v1/admin/subscribe-emails/{id}/mark-not-added
    @Operation(summary = "Admin mark subscriber as not added to external email group")
    @PatchMapping({"/api/admin/subscribe-emails/{id}/mark-not-added", "/api/v1/admin/subscribe-emails/{id}/mark-not-added"})
    public ResponseEntity<SubscribeEmailResponseDTO> markAsNotAdded(@PathVariable Long id) {
        return ResponseEntity.ok(service.markAsNotAdded(id));
    }

    // ── Admin — delete subscriber record ──────────────────
    // DELETE /api/admin/subscribe-emails/{id} or /api/v1/admin/subscribe-emails/{id}
    @Operation(summary = "Admin permanently delete subscriber record")
    @DeleteMapping({"/api/admin/subscribe-emails/{id}", "/api/v1/admin/subscribe-emails/{id}"})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
