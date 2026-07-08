package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findByUserOrderByCreatedAtDesc(User user);
}
