package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_activity_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false, length = 50)
    private String actionType; // TRIP_GENERATED, TRIP_EDITED, TRIP_REGENERATED, STATUS_CHANGED, TRIP_CONFIRMED, TRIP_RESTORED, SHARE_REVOKED

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 100)
    private String performedBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
