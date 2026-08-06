package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.PlannerMetadata;
import com.exploreceylon.backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlannerMetadataRepository extends JpaRepository<PlannerMetadata, Long> {
    Optional<PlannerMetadata> findByTrip(Trip trip);
    Optional<PlannerMetadata> findByTripId(Long tripId);
}
