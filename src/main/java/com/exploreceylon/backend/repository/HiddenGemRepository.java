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

    // Get by title (used to resolve AI-generated itinerary items back to DB rows)
    java.util.Optional<HiddenGem> findByTitleIgnoreCase(String title);

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

    // ── Geo queries (SQL-level haversine, no full-table load) ──────────
    // Same haversine formula/NULL-permissive convention as
    // DestinationRepository — see comments there.

    @Query(value =
            "SELECT * FROM hidden_gems g WHERE g.approved = true " +
            "AND g.latitude IS NOT NULL AND g.longitude IS NOT NULL " +
            "ORDER BY (6371 * ACOS(LEAST(1.0, GREATEST(-1.0, " +
            "COS(RADIANS(:lat)) * COS(RADIANS(g.latitude)) * " +
            "COS(RADIANS(g.longitude) - RADIANS(:lng)) + " +
            "SIN(RADIANS(:lat)) * SIN(RADIANS(g.latitude)))))) ASC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<HiddenGem> findNearestTo(@Param("lat") double lat,
                                   @Param("lng") double lng,
                                   @Param("limit") int limit);

    @Query(value =
            "SELECT * FROM hidden_gems g WHERE g.approved = true " +
            "AND g.latitude IS NOT NULL AND g.longitude IS NOT NULL " +
            "AND (CAST(:budgetLevel AS varchar) = '' OR g.budget_level IS NULL " +
            "     OR g.budget_level = CAST(:budgetLevel AS varchar)) " +
            "AND (6371 * ACOS(LEAST(1.0, GREATEST(-1.0, " +
            "COS(RADIANS(:lat)) * COS(RADIANS(g.latitude)) * " +
            "COS(RADIANS(g.longitude) - RADIANS(:lng)) + " +
            "SIN(RADIANS(:lat)) * SIN(RADIANS(g.latitude)))))) <= :radiusKm " +
            "ORDER BY g.rating DESC",
            nativeQuery = true)
    List<HiddenGem> findWithinRadius(@Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("radiusKm") double radiusKm,
                                      @Param("budgetLevel") String budgetLevel);
}