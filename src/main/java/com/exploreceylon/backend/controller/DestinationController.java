package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.destination.CreateDestinationRequest;
import com.exploreceylon.backend.dto.destination.DestinationResponse;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.DestinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    // GET /api/v1/destinations
    @GetMapping
    public ResponseEntity<List<DestinationResponse>> getAll(
            @RequestParam(required = false)
            Destination.DestinationCategory category,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String month,
            @RequestParam(value = "includeAll",required = false, defaultValue = "false") boolean includeAll) {
        return ResponseEntity.ok(
                destinationService.getAllDestinations(
                        category, province, month, includeAll));
    }

    // GET /api/v1/destinations/featured
    @GetMapping("/featured")
    public ResponseEntity<List<DestinationResponse>> getFeatured() {
        return ResponseEntity.ok(
                destinationService.getFeatured());
    }

    // GET /api/v1/destinations/search
    @GetMapping("/search")
    public ResponseEntity<List<DestinationResponse>> search(
            @RequestParam String keyword) {
        return ResponseEntity.ok(
                destinationService.search(keyword));
    }

    // GET /api/v1/destinations/{id}
    @GetMapping("/{id}")
    public ResponseEntity<DestinationResponse> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                destinationService.getById(id));
    }

    // POST /api/v1/destinations (Admin)
    @PostMapping
    public ResponseEntity<DestinationResponse> create(
            @Valid @RequestBody CreateDestinationRequest request) {
        return ResponseEntity.ok(
                destinationService.create(request));
    }

    // PUT /api/v1/destinations/{id} (Admin)
    @PutMapping("/{id}")
    public ResponseEntity<DestinationResponse> update(
            @PathVariable Long id,
            @RequestBody CreateDestinationRequest request) {
        return ResponseEntity.ok(
                destinationService.update(id, request));
    }

    // PUT /api/v1/destinations/{id}/featured (Admin)
    @PutMapping("/{id}/featured")
    public ResponseEntity<DestinationResponse> toggleFeatured(
            @PathVariable Long id,
            @RequestParam Boolean featured) {
        return ResponseEntity.ok(
                destinationService.toggleFeatured(id, featured));
    }

    // DELETE /api/v1/destinations/{id} (Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        destinationService.delete(id);
        return ResponseEntity.noContent().build();
}

    // PUT /api/v1/destinations/{id}/active (Admin)
    @PutMapping("/{id}/active")
    public ResponseEntity<DestinationResponse> toggleActive(
            @PathVariable Long id,
            @RequestParam Boolean active) {
        return ResponseEntity.ok(
                destinationService.toggleActive(id, active));
    }
}