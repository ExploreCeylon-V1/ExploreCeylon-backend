package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Trip> findByShareToken(String shareToken);
    List<Trip> findByUserIdAndStatus(Long userId, Trip.TripStatus status);
}