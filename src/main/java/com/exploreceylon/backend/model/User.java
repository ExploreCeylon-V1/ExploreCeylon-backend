package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // Nullable: Google-only users have no password until they set one.
    @Column(nullable = true)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.TRAVELER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    // Google's stable "sub" claim. Set on first Google sign-in/linking; never used to
    // decide authProvider on its own — a LOCAL user can hold a googleId and still be LOCAL.
    @Column(unique = true)
    private String googleId;

    private String nationality;

    @Builder.Default
    private String language = "en";

    private String phone;

    private String profilePhoto;

    @Builder.Default
    private Boolean emailVerified = false;

    @Builder.Default
    private Boolean phoneVerified = false;

    @Builder.Default
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.NOT_SUBMITTED;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (kycStatus == null) {
            kycStatus = KycStatus.NOT_SUBMITTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── UserDetails methods ────────────────────────────────────
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired()    { return true; }

    @Override
    public boolean isAccountNonLocked()     { return true; }

    @Override
    public boolean isCredentialsNonExpired(){ return true; }

    @Override
    public boolean isEnabled()              { return active == null || active; }

    // ── Role Enum ──────────────────────────────────────────────
    public enum Role {
        TRAVELER, ADMIN
    }

    // ── Auth Provider Enum ────────────────────────────────────
    public enum AuthProvider {
        LOCAL, GOOGLE
    }

    // ── KYC Status Enum ───────────────────────────────────────
    public enum KycStatus {
        NOT_SUBMITTED, PENDING, APPROVED, REJECTED
    }
}