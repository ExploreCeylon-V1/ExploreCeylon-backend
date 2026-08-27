package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.SubscribeEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscribeEmailRepository extends JpaRepository<SubscribeEmail, Long> {

    boolean existsByEmail(String email);

    Optional<SubscribeEmail> findByEmail(String email);

    List<SubscribeEmail> findByAddedToGroupOrderBySubscribedAtDesc(boolean addedToGroup);

    List<SubscribeEmail> findAllByOrderBySubscribedAtDesc();
}
