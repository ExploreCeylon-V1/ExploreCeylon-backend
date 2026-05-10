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
}