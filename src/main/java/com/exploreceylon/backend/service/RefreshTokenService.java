package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.RefreshToken;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public RefreshToken createRefreshToken(User user, LoginHistory.LoginType loginType,
                                            String ipAddress, String deviceInfo) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .loginType(loginType)
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiryDate(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    // Throws on missing/revoked/expired — caller relies on GlobalExceptionHandler for the 400.
    public RefreshToken verifyAndGet(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            throw new RuntimeException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired. Please log in again");
        }
        return refreshToken;
    }

    public void revoke(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    public void revokeAllForUser(User user) {
        List<RefreshToken> active = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        active.forEach(rt -> rt.setRevoked(true));
        refreshTokenRepository.saveAll(active);
    }

    public List<RefreshToken> getActiveSessions(User user) {
        return refreshTokenRepository.findAllByUserAndRevokedFalseAndExpiryDateAfter(user, LocalDateTime.now());
    }

    // Nightly cleanup of tokens that expired more than a day ago.
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        refreshTokenRepository.deleteAllByExpiryDateBefore(cutoff);
        log.info("Purged expired refresh tokens older than {}", cutoff);
    }
}
