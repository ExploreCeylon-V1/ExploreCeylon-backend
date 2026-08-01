package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.destination.CreateDestinationReviewRequest;
import com.exploreceylon.backend.dto.destination.DestinationReviewResponse;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.DestinationReview;
import com.exploreceylon.backend.repository.DestinationRepository;
import com.exploreceylon.backend.repository.Destinationreviewrepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinationReviewService {

    private final Destinationreviewrepository reviewRepository;
    private final DestinationRepository destinationRepository;

    public List<DestinationReviewResponse> getReviewsForDestination(Long destinationId) {
        return reviewRepository.findByDestination_IdOrderByCreatedAtDesc(destinationId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DestinationReviewResponse addReview(Long destinationId, CreateDestinationReviewRequest request) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new EntityNotFoundException("Destination not found with id: " + destinationId));

        DestinationReview review = DestinationReview.builder()
                .destination(destination)
                .travelerName(request.getTravelerName())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        DestinationReview saved = reviewRepository.save(review);

        syncDestinationRatingStats(destination);

        return toResponse(saved);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        DestinationReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found with id: " + reviewId));

        Destination destination = review.getDestination();
        reviewRepository.delete(review);
        syncDestinationRatingStats(destination);
    }

    // Recalculates Destination.rating and reviewCount from destination_reviews
    private void syncDestinationRatingStats(Destination destination) {
        Double avgRating = reviewRepository.findAverageRatingByDestinationId(destination.getId());
        Integer count = reviewRepository.countByDestinationId(destination.getId());

        destination.setRating(avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0);
        destination.setReviewCount(count != null ? count : 0);
        destinationRepository.save(destination);
    }

    private DestinationReviewResponse toResponse(DestinationReview review) {
        DestinationReviewResponse response = new DestinationReviewResponse();
        response.setId(review.getId());
        response.setDestinationId(review.getDestination().getId());
        response.setTravelerName(review.getTravelerName());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());
        return response;
    }
}