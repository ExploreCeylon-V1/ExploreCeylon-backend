package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.HiddenGem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HiddenGemRepository extends JpaRepository<HiddenGem, Long> {

    // All approved gems
    List<HiddenGem> findByApprovedTrueOrderByRatingDesc();

    // Filter by category
    List<HiddenGem> findByCategoryAndApprovedTrueOrderByRatingDesc(
            HiddenGem.GemCategory category);

    // Filter by district
    List<HiddenGem> findByDistrictIgnoreCaseAndApprovedTrueOrderByRatingDesc(
            String district);

    // Filter by category + district
    List<HiddenGem> findByCategoryAndDistrictIgnoreCaseAndApprovedTrue(
            HiddenGem.GemCategory category, String district);

    // Keyword search
    @Query("SELECT g FROM HiddenGem g WHERE g.approved = true AND " +
           "(LOWER(g.title) LIKE LOWER(CONCAT('%',:keyword,'%')) OR " +
           "LOWER(g.description) LIKE LOWER(CONCAT('%',:keyword,'%')) OR " +
           "LOWER(g.district) LIKE LOWER(CONCAT('%',:keyword,'%')))")
    List<HiddenGem> searchByKeyword(@Param("keyword") String keyword);

    // Pending approval (admin)
    List<HiddenGem> findByApprovedFalseOrderByCreatedAtDesc();

    // Random approved gems (for AI injection)
    @Query("SELECT g FROM HiddenGem g WHERE g.approved = true " +
           "ORDER BY FUNCTION('RANDOM')")
    List<HiddenGem> findRandomApprovedGems();
}