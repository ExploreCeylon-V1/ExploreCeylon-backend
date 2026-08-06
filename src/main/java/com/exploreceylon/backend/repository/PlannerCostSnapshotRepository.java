package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.PlannerCostSnapshot;
import com.exploreceylon.backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlannerCostSnapshotRepository extends JpaRepository<PlannerCostSnapshot, Long> {
    Optional<PlannerCostSnapshot> findByTrip(Trip trip);
    Optional<PlannerCostSnapshot> findByTripId(Long tripId);
}
