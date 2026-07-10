package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeType type;   // EMAIL | PHONE

    @Column(nullable = false)
    private String code;     // 6-digit

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean consumed = false;

    // Failed verify attempts against THIS code; locks after the configured max.
    @Builder.Default
    private int attempts = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum CodeType {
        EMAIL, PHONE, PASSWORD_RESET
    }
}
