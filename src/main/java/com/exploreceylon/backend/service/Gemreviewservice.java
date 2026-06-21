package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.review.Createreviewrequest;
import com.exploreceylon.backend.dto.review.Reviewresponse;
import com.exploreceylon.backend.model.Gemreview;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.repository.Gemreviewrepository;
import com.exploreceylon.backend.repository.HiddenGemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Gemreviewservice {

    private final Gemreviewrepository reviewRepository;
    private final HiddenGemRepository gemRepository;

    public List<Reviewresponse> getReviewsForGem(Long gemId) {
        return reviewRepository.findByGem_IdOrderByCreatedAtDesc(gemId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Reviewresponse addReview(Long gemId, Createreviewrequest request) {
        HiddenGem gem = gemRepository.findById(gemId)
                .orElseThrow(() -> new EntityNotFoundException("Gem not found with id: " + gemId));

        Gemreview review = Gemreview.builder()
                .gem(gem)
                .travelerName(request.getTravelerName())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Gemreview saved = reviewRepository.save(review);

        syncGemRatingStats(gem);

        return toResponse(saved);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Gemreview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found with id: " + reviewId));

        HiddenGem gem = review.getGem();
        reviewRepository.delete(review);
        syncGemRatingStats(gem);
    }

    // Recalculates HiddenGem.rating and reviewCount from gem_reviews
    private void syncGemRatingStats(HiddenGem gem) {
        Double avgRating = reviewRepository.findAverageRatingByGemId(gem.getId());
        Integer count = reviewRepository.countByGemId(gem.getId());

        gem.setRating(avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0);
        gem.setReviewCount(count != null ? count : 0);
        gemRepository.save(gem);
    }

    private Reviewresponse toResponse(Gemreview review) {
        Reviewresponse response = new Reviewresponse();
        response.setId(review.getId());
        response.setGemId(review.getGem().getId());
        response.setTravelerName(review.getTravelerName());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());
        return response;
    }
}