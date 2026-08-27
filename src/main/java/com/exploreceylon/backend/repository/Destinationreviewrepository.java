package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.DestinationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface Destinationreviewrepository extends JpaRepository<DestinationReview, Long> {

    // GET /api/v1/destinations/{destinationId}/reviews
    List<DestinationReview> findByDestination_IdOrderByCreatedAtDesc(Long destinationId);

    @Query("SELECT AVG(r.rating) FROM DestinationReview r WHERE r.destination.id = :destinationId")
    Double findAverageRatingByDestinationId(@Param("destinationId") Long destinationId);

    @Query("SELECT COUNT(r) FROM DestinationReview r WHERE r.destination.id = :destinationId")
    Integer countByDestinationId(@Param("destinationId") Long destinationId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM DestinationReview r WHERE r.destination.id = :destinationId")
    void deleteByDestinationId(@Param("destinationId") Long destinationId);
}