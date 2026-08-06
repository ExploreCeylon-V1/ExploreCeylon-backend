package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.TripActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripActivityLogRepository extends JpaRepository<TripActivityLog, Long> {
    List<TripActivityLog> findByTripIdOrderByCreatedAtDesc(Long tripId);
}
