package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.VerificationCode;
import com.exploreceylon.backend.model.VerificationCode.CodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    // Latest unconsumed code of a given type for a user
    Optional<VerificationCode> findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(
            Long userId, CodeType type);

    // Drop all codes of a channel — used when the email/phone value changes.
    @Transactional
    void deleteByUserIdAndType(Long userId, CodeType type);
}
