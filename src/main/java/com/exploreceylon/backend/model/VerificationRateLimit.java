package com.exploreceylon.backend.model;

import com.exploreceylon.backend.model.VerificationCode.CodeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per (user, channel) tracking send-rate state:
 *  - lastSentAt  → 60s cooldown between sends
 *  - windowStart + sendsInWindow → rolling-hour send cap
 *  - lockedUntil → 30-minute lockout after abuse (hourly cap breach or too many
 *                  failed verify attempts)
 */
@Entity
@Table(name = "verification_rate_limits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerificationRateLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeType type;   // EMAIL | PHONE

    private LocalDateTime lastSentAt;

    private LocalDateTime windowStart;

    @Builder.Default
    private int sendsInWindow = 0;

    private LocalDateTime lockedUntil;
}
