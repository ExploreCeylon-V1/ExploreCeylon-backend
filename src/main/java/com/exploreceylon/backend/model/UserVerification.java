package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVerification implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "front_image_url", nullable = false, length = 500)
    private String frontImageUrl;

    @Column(name = "back_image_url", length = 500)
    private String backImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Transient
    @Builder.Default
    private boolean isNewEntity = true;

    @Override
    public boolean isNew() {
        return isNewEntity || id == null;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNewEntity = false;
    }

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }

    public enum DocumentType {
        NIC,
        DRIVING_LICENSE,
        PASSPORT
    }

    public enum VerificationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
