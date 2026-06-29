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

    // ✅ Count by user
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    // ✅ Count by status
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.status = :status")
    long countByStatus(@Param("status") Trip.TripStatus status);
}