package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.GuideReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuideReviewRepository
        extends JpaRepository<GuideReview, Long> {

    List<GuideReview> findByGuideIdOrderByCreatedAtDesc(Long guideId);

    @Query("SELECT AVG(r.rating) FROM GuideReview r " +
           "WHERE r.guide.id = :guideId")
    Double findAverageRatingByGuideId(@Param("guideId") Long guideId);

    Long countByGuideId(Long guideId);
}