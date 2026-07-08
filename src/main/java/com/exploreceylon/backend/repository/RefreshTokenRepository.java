package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.RefreshToken;
import com.exploreceylon.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findAllByUserAndRevokedFalseAndExpiryDateAfter(User user, LocalDateTime now);
    List<RefreshToken> findAllByUserAndRevokedFalse(User user);
    void deleteAllByExpiryDateBefore(LocalDateTime cutoff);
}
