package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.GuidePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GuidePaymentRepository extends JpaRepository<GuidePayment, Long> {
    Optional<GuidePayment> findByGuideId(Long guideId);
}