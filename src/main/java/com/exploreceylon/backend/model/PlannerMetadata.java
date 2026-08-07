package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "planner_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Builder.Default
    private String plannerVersion = "13.0";

    private Double qualityScore;
    private Long generationTimeMs;
    private Long executionTimeMs;

    @Builder.Default
    private Boolean routeReuse = true;

    @Builder.Default
    private String aiProvider = "GROQ / ExploreCeylon Narrative";

    @Builder.Default
    private Boolean fallbackUsed = false;

    @Builder.Default
    private Integer versionNumber = 1;

    @Builder.Default
    private Integer editCount = 0;

    private LocalDateTime lastRegeneratedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
