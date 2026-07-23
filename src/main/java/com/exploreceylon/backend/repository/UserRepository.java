package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    long countByActiveTrue();
    long countByEmailVerifiedTrue();
    long countByCreatedAtAfter(LocalDateTime cutoff);
    long countByRole(User.Role role);

    // Recent registrations for the admin dashboard's Recent Activity card.
    List<User> findTop5ByOrderByCreatedAtDesc();

    // Registrations-by-month grouping, reused by Analytics.
    @Query("SELECT FUNCTION('date_trunc', 'month', u.createdAt), COUNT(u) " +
           "FROM User u GROUP BY FUNCTION('date_trunc', 'month', u.createdAt)")
    List<Object[]> countGroupedByMonth();
}
