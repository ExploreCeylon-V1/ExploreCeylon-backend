package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Gemreview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface Gemreviewrepository extends JpaRepository<Gemreview, Long> {

    // GET /api/v1/gems/{gemId}/reviews
    List<Gemreview> findByGem_IdOrderByCreatedAtDesc(Long gemId);

    @Query("SELECT AVG(r.rating) FROM Gemreview r WHERE r.gem.id = :gemId")
    Double findAverageRatingByGemId(@Param("gemId") Long gemId);

    @Query("SELECT COUNT(r) FROM Gemreview r WHERE r.gem.id = :gemId")
    Integer countByGemId(@Param("gemId") Long gemId);
}