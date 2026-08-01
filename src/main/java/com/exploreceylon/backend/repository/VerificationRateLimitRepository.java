package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.VerificationCode.CodeType;
import com.exploreceylon.backend.model.VerificationRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface VerificationRateLimitRepository extends JpaRepository<VerificationRateLimit, Long> {

    Optional<VerificationRateLimit> findByUserIdAndType(Long userId, CodeType type);

    @Transactional
    void deleteByUserIdAndType(Long userId, CodeType type);
}
