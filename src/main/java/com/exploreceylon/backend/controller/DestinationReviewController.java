package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.destination.CreateDestinationReviewRequest;
import com.exploreceylon.backend.dto.destination.DestinationReviewResponse;
import com.exploreceylon.backend.service.DestinationReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations/{destinationId}/reviews")
@RequiredArgsConstructor
public class DestinationReviewController {

    private final DestinationReviewService reviewService;

    // GET /api/v1/destinations/{destinationId}/reviews
    @GetMapping
    public ResponseEntity<List<DestinationReviewResponse>> getReviewsForDestination(
            @PathVariable Long destinationId) {
        return ResponseEntity.ok(reviewService.getReviewsForDestination(destinationId));
    }

    // POST /api/v1/destinations/{destinationId}/reviews (Authenticated travelers)
    @PostMapping
    public ResponseEntity<DestinationReviewResponse> addReview(
            @PathVariable Long destinationId,
            @Valid @RequestBody CreateDestinationReviewRequest request) {
        return ResponseEntity.ok(reviewService.addReview(destinationId, request));
    }

    // DELETE /api/v1/destinations/{destinationId}/reviews/{reviewId} (Admin or owner)
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long destinationId,
            @PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok().build();
    }
}