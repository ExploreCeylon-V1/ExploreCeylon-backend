package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only AI Estimated Cost Snapshot entity for Phase 13.
 * IMPORTANT: This entity stores pre-trip AI estimated costs ONLY.
 * It NEVER overwrites or interacts with manual user expenses in the Budget Tracker.
 */
@Entity
@Table(name = "planner_cost_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerCostSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    private Double grandTotal;
    private Double transportCost;
    private Double entranceTicketsCost;
    private Double foodCost;
    private Double hiddenGemsCost;
    private Double parkingCost;
    private Double miscCost;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
