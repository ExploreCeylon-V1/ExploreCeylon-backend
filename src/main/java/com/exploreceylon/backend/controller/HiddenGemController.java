package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.gem.CreateGemRequest;
import com.exploreceylon.backend.dto.gem.GemResponse;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.service.HiddenGemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gems")
@RequiredArgsConstructor
public class HiddenGemController {

    private final HiddenGemService gemService;

    // GET /api/v1/gems
    @GetMapping
    public ResponseEntity<List<GemResponse>> getAllGems(
            @RequestParam(required = false) HiddenGem.GemCategory category,
            @RequestParam(required = false) String district) {
        return ResponseEntity.ok(
                gemService.getAllGems(category, district));
    }

    // GET /api/v1/gems/{id}
    @GetMapping("/{id}")
    public ResponseEntity<GemResponse> getGemById(
            @PathVariable Long id) {
        return ResponseEntity.ok(gemService.getGemById(id));
    }

    // GET /api/v1/gems/search?keyword=waterfall
    @GetMapping("/search")
    public ResponseEntity<List<GemResponse>> searchGems(
            @RequestParam String keyword) {
        return ResponseEntity.ok(gemService.searchGems(keyword));
    }

    // GET /api/v1/gems/pending (Admin)
    @GetMapping("/pending")
    public ResponseEntity<List<GemResponse>> getPendingGems() {
        return ResponseEntity.ok(gemService.getPendingGems());
    }

    // POST /api/v1/gems/submit (Authenticated users)
    @PostMapping("/submit")
    public ResponseEntity<GemResponse> submitGem(
            @Valid @RequestBody CreateGemRequest request) {
        return ResponseEntity.ok(gemService.submitGem(request));
    }

    // POST /api/v1/gems (Admin — direct add)
    @PostMapping
    public ResponseEntity<GemResponse> addGem(
            @Valid @RequestBody CreateGemRequest request) {
        return ResponseEntity.ok(gemService.addGem(request));
    }

    // PUT /api/v1/gems/{id}/approve (Admin)
    @PutMapping("/{id}/approve")
    public ResponseEntity<GemResponse> approveGem(
            @PathVariable Long id) {
        return ResponseEntity.ok(gemService.approveGem(id));
    }

    // PUT /api/v1/gems/{id} (Admin)
    @PutMapping("/{id}")
    public ResponseEntity<GemResponse> updateGem(
            @PathVariable Long id,
            @RequestBody CreateGemRequest request) {
        return ResponseEntity.ok(
                gemService.updateGem(id, request));
    }

    // DELETE /api/v1/gems/{id} (Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGem(
            @PathVariable Long id) {
        gemService.deleteGem(id);
        return ResponseEntity.ok().build();
    }
}