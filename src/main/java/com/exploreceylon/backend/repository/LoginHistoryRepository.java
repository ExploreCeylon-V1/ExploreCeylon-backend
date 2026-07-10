package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findByUserOrderByCreatedAtDesc(User user);

    // Batch "last login per user" for a page of users at once — see
    // TripRepository.countByUserIdIn for the same N+1-avoidance pattern.
    @Query("SELECT lh.user.id, MAX(lh.createdAt) FROM LoginHistory lh " +
           "WHERE lh.user.id IN :userIds GROUP BY lh.user.id")
    List<Object[]> findLastLoginByUserIdIn(@Param("userIds") List<Long> userIds);
}
