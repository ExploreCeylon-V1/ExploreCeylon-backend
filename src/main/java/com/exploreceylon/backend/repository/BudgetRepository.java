package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository
        extends JpaRepository<Budget, Long> {

    Optional<Budget> findByTripId(Long tripId);
    List<Budget> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByTripId(Long tripId);
}