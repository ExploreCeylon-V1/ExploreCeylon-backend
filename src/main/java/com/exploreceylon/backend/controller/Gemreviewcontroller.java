package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.review.Createreviewrequest;
import com.exploreceylon.backend.dto.review.Reviewresponse;
import com.exploreceylon.backend.service.Gemreviewservice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gems/{gemId}/reviews")
@RequiredArgsConstructor
public class Gemreviewcontroller {

    private final Gemreviewservice reviewService;

    // GET /api/v1/gems/{gemId}/reviews
    @GetMapping
    public ResponseEntity<List<Reviewresponse>> getReviewsForGem(
            @PathVariable Long gemId) {
        return ResponseEntity.ok(reviewService.getReviewsForGem(gemId));
    }

    // POST /api/v1/gems/{gemId}/reviews (Authenticated travelers)
    @PostMapping
    public ResponseEntity<Reviewresponse> addReview(
            @PathVariable Long gemId,
            @Valid @RequestBody Createreviewrequest request) {
        return ResponseEntity.ok(reviewService.addReview(gemId, request));
    }

    // DELETE /api/v1/gems/{gemId}/reviews/{reviewId} (Admin or review owner)
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long gemId,
            @PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok().build();
    }
}