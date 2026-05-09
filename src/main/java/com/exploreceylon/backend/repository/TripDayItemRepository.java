package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.TripDayItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripDayItemRepository extends JpaRepository<TripDayItem, Long> {
    List<TripDayItem> findByTripDayIdOrderByOrderIndex(Long tripDayId);
    void deleteByTripDayId(Long tripDayId);
}