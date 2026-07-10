package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    // ✅ user.id navigate කරනවා (user object ේ id field)
    @Query("SELECT t FROM Trip t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<Trip> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    // ✅ Share token by
    Optional<Trip> findByShareToken(String shareToken);

    // ✅ Admin — all trips with user info
    @Query("SELECT t FROM Trip t JOIN FETCH t.user " +
           "ORDER BY t.createdAt DESC")
    List<Trip> findAllWithUserOrderByCreatedAtDesc();

    // Recent trips for the admin dashboard's Recent Activity card — fetches
    // the owning user in the same query so the mapper doesn't lazy-load
    // t.getUser() once per row (N+1).
    @Query("SELECT t FROM Trip t JOIN FETCH t.user ORDER BY t.createdAt DESC")
    List<Trip> findRecentWithUser(org.springframework.data.domain.Pageable pageable);

    // ✅ Count by user
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    // ✅ Count by status
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.status = :status")
    long countByStatus(@Param("status") Trip.TripStatus status);

    // Batch count for a page of users at once — avoids one query per row
    // (see AdminService.toUserResponse, which used to call countByUserId
    // per user; this collapses that N+1 into a single grouped query).
    @Query("SELECT t.user.id, COUNT(t) FROM Trip t WHERE t.user.id IN :userIds GROUP BY t.user.id")
    List<Object[]> countByUserIdIn(@Param("userIds") List<Long> userIds);

    // Registrations-by-month style grouping for trips, reused by Analytics.
    @Query("SELECT FUNCTION('date_trunc', 'month', t.createdAt), COUNT(t) " +
           "FROM Trip t GROUP BY FUNCTION('date_trunc', 'month', t.createdAt)")
    List<Object[]> countGroupedByMonth();
}