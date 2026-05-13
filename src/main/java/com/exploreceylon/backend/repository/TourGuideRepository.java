package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.TourGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TourGuideRepository
        extends JpaRepository<TourGuide, Long> {

    // All verified + available guides
    List<TourGuide> findByVerifiedTrueAndAvailableTrueOrderByRatingDesc();

    // Filter by district
    List<TourGuide> findByDistrictIgnoreCaseAndVerifiedTrueAndAvailableTrue(
            String district);

    // Filter by max price
    List<TourGuide> findByPricePerDayLessThanEqualAndVerifiedTrue(
            Double maxPrice);

    // Filter by district + max price
    List<TourGuide> findByDistrictIgnoreCaseAndPricePerDayLessThanEqualAndVerifiedTrue(
            String district, Double maxPrice);

    // Search by keyword (name, bio, specialties)
    @Query("SELECT g FROM TourGuide g WHERE g.verified = true AND (" +
           "LOWER(g.fullName) LIKE LOWER(CONCAT('%',:kw,'%')) OR " +
           "LOWER(g.bio) LIKE LOWER(CONCAT('%',:kw,'%')) OR " +
           "LOWER(g.specialties) LIKE LOWER(CONCAT('%',:kw,'%')) OR " +
           "LOWER(g.district) LIKE LOWER(CONCAT('%',:kw,'%')))")
    List<TourGuide> searchGuides(@Param("kw") String keyword);

    // Filter by language
    @Query("SELECT g FROM TourGuide g WHERE g.verified = true AND " +
           "LOWER(g.languages) LIKE LOWER(CONCAT('%',:lang,'%')) " +
           "ORDER BY g.rating DESC")
    List<TourGuide> findByLanguage(@Param("lang") String language);

    // Filter by specialty
    @Query("SELECT g FROM TourGuide g WHERE g.verified = true AND " +
           "LOWER(g.specialties) LIKE LOWER(CONCAT('%',:spec,'%')) " +
           "ORDER BY g.rating DESC")
    List<TourGuide> findBySpecialty(@Param("spec") String specialty);
}



