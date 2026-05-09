package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripDayRepository extends JpaRepository<TripDay, Long> {
    List<TripDay> findByTripIdOrderByDayNumber(Long tripId);
    void deleteByTripId(Long tripId);
}