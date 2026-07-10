package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DestinationRepository
        extends JpaRepository<Destination, Long> {

    // All active destinations
    List<Destination> findByActiveTrueOrderByRatingDesc();

    // Featured destinations
    List<Destination> findByFeaturedTrueAndActiveTrueOrderByRatingDesc();

    // Filter by category
    List<Destination> findByCategoryAndActiveTrueOrderByRatingDesc(
            Destination.DestinationCategory category);

    // Filter by province
    List<Destination> findByProvinceIgnoreCaseAndActiveTrueOrderByRatingDesc(
            String province);

    // Filter by category + province
    List<Destination> findByCategoryAndProvinceIgnoreCaseAndActiveTrue(
            Destination.DestinationCategory category, String province);

    // Search by keyword
    @Query("SELECT d FROM Destination d WHERE d.active = true AND (" +
           "LOWER(d.name)             LIKE LOWER(CONCAT('%',:kw,'%')) OR " +
           "LOWER(d.district)         LIKE LOWER(CONCAT('%',:kw,'%')) OR " +
           "LOWER(d.description)      LIKE LOWER(CONCAT('%',:kw,'%')) OR " +
           "LOWER(d.activities)       LIKE LOWER(CONCAT('%',:kw,'%'))) " +
           "ORDER BY CASE WHEN LOWER(d.name) = LOWER(:kw) THEN 0 ELSE 1 END, d.rating DESC")
    List<Destination> searchDestinations(@Param("kw") String keyword);

    // Filter by best month
    @Query("SELECT d FROM Destination d WHERE d.active = true AND " +
           "LOWER(d.bestMonths) LIKE LOWER(CONCAT('%',:month,'%')) " +
           "ORDER BY d.rating DESC")
    List<Destination> findByBestMonth(@Param("month") String month);

    // Get by name
    java.util.Optional<Destination> findByNameIgnoreCase(String name);

    // Top-reviewed destinations — used by the admin dashboard's Top Lists
    // card and by Analytics' destination-popularity chart.
    List<Destination> findTop10ByOrderByReviewCountDesc();

    // ── Geo queries (SQL-level haversine, no full-table load) ──────────
    //
    // Standard haversine formula using Postgres' built-in trig functions
    // (SIN/COS/ACOS/RADIANS) — works without the earthdistance/cube
    // extension. The ACOS argument is clamped to [-1, 1] with
    // LEAST/GREATEST since floating-point rounding can push it a hair
    // outside that range for near-identical points, which would
    // otherwise make ACOS return NaN.
    //
    // budgetLevel is NULL-permissive: pass "" (empty string, never null —
    // Postgres can't infer a type for a null native-query parameter) to
    // skip the filter entirely, and rows with a NULL budget_level always
    // match (unknown tier is never used to exclude a candidate).

    // Distance-sorted nearest destinations to a point — replaces the old
    // "load all active rows, sort in Java" findNearby().
    @Query(value =
            "SELECT * FROM destinations d WHERE d.active = true " +
            "AND d.latitude IS NOT NULL AND d.longitude IS NOT NULL " +
            "ORDER BY (6371 * ACOS(LEAST(1.0, GREATEST(-1.0, " +
            "COS(RADIANS(:lat)) * COS(RADIANS(d.latitude)) * " +
            "COS(RADIANS(d.longitude) - RADIANS(:lng)) + " +
            "SIN(RADIANS(:lat)) * SIN(RADIANS(d.latitude)))))) ASC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Destination> findNearestTo(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("limit") int limit);

    // Coarse pre-filter for corridor candidate selection: every active
    // destination within radiusKm of a point (typically the corridor
    // midpoint), optionally restricted to a budget tier. Precise detour
    // math (origin+dest routing) is done in Java afterwards over this
    // already-small result set.
    @Query(value =
            "SELECT * FROM destinations d WHERE d.active = true " +
            "AND d.latitude IS NOT NULL AND d.longitude IS NOT NULL " +
            "AND (CAST(:budgetLevel AS varchar) = '' OR d.budget_level IS NULL " +
            "     OR d.budget_level = CAST(:budgetLevel AS varchar)) " +
            "AND (6371 * ACOS(LEAST(1.0, GREATEST(-1.0, " +
            "COS(RADIANS(:lat)) * COS(RADIANS(d.latitude)) * " +
            "COS(RADIANS(d.longitude) - RADIANS(:lng)) + " +
            "SIN(RADIANS(:lat)) * SIN(RADIANS(d.latitude)))))) <= :radiusKm " +
            "ORDER BY d.rating DESC",
            nativeQuery = true)
    List<Destination> findWithinRadius(@Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("radiusKm") double radiusKm,
                                        @Param("budgetLevel") String budgetLevel);
}