package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.UserVerification;
import com.exploreceylon.backend.model.UserVerification.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserVerificationRepository extends JpaRepository<UserVerification, UUID>, JpaSpecificationExecutor<UserVerification> {

    Optional<UserVerification> findFirstByUserIdOrderBySubmittedAtDesc(Long userId);

    boolean existsByUserIdAndStatus(Long userId, VerificationStatus status);

    long countByStatus(VerificationStatus status);

    @Query("SELECT uv FROM UserVerification uv JOIN uv.user u " +
           "WHERE (:status IS NULL OR uv.status = :status) " +
           "AND (:search IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(uv.nationality) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<UserVerification> findAllFiltered(
            @Param("status") VerificationStatus status,
            @Param("search") String search,
            Pageable pageable);
}
