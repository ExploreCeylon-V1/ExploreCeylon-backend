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
           "ORDER BY d.rating DESC")
    List<Destination> searchDestinations(@Param("kw") String keyword);

    // Filter by best month
    @Query("SELECT d FROM Destination d WHERE d.active = true AND " +
           "LOWER(d.bestMonths) LIKE LOWER(CONCAT('%',:month,'%')) " +
           "ORDER BY d.rating DESC")
    List<Destination> findByBestMonth(@Param("month") String month);

    // Get by name
    java.util.Optional<Destination> findByNameIgnoreCase(String name);
}