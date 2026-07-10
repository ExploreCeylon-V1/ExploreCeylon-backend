package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Get by title (used to resolve AI-generated itinerary items back to DB rows)
    java.util.Optional<Event> findByTitleIgnoreCase(String title);

    // Filter by category
    List<Event> findByCategoryOrderByStartDate(Event.EventCategory category);

    // Filter by region
    List<Event> findByRegionIgnoreCaseOrderByStartDate(String region);

    // Filter by month
    @Query("SELECT e FROM Event e WHERE " +
           "MONTH(e.startDate) = :month OR MONTH(e.endDate) = :month " +
           "ORDER BY e.startDate")
    List<Event> findByMonth(@Param("month") int month);

    // Filter by region + category
    List<Event> findByRegionIgnoreCaseAndCategoryOrderByStartDate(
            String region, Event.EventCategory category);

    // Trip sync — events that overlap with trip dates
    @Query("SELECT e FROM Event e WHERE " +
           "e.startDate <= :endDate AND e.endDate >= :startDate " +
           "ORDER BY e.startDate")
    List<Event> findEventsBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Upcoming events (next 30 days)
    @Query("SELECT e FROM Event e WHERE " +
           "e.startDate >= :today AND e.startDate <= :future " +
           "ORDER BY e.startDate")
    List<Event> findUpcomingEvents(
            @Param("today") LocalDate today,
            @Param("future") LocalDate future);

    // Filter by month + region
    @Query("SELECT e FROM Event e WHERE " +
           "(MONTH(e.startDate) = :month OR MONTH(e.endDate) = :month) " +
           "AND LOWER(e.region) = LOWER(:region) " +
           "ORDER BY e.startDate")
    List<Event> findByMonthAndRegion(
            @Param("month") int month,
            @Param("region") String region);

    // Events overlapping the trip dates AND within radiusKm of a point —
    // coarse SQL pre-filter for corridor-aware event selection (Phase 3).
    // Events without coordinates are excluded here since there's nothing
    // to measure distance against; date-only matching still happens via
    // findEventsBetweenDates for callers that don't need proximity.
    @Query(value =
            "SELECT * FROM events e WHERE " +
            "e.start_date <= :endDate AND e.end_date >= :startDate " +
            "AND e.latitude IS NOT NULL AND e.longitude IS NOT NULL " +
            "AND (6371 * ACOS(LEAST(1.0, GREATEST(-1.0, " +
            "COS(RADIANS(:lat)) * COS(RADIANS(e.latitude)) * " +
            "COS(RADIANS(e.longitude) - RADIANS(:lng)) + " +
            "SIN(RADIANS(:lat)) * SIN(RADIANS(e.latitude)))))) <= :radiusKm " +
            "ORDER BY e.start_date",
            nativeQuery = true)
    List<Event> findEventsBetweenDatesWithinRadius(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm);
}