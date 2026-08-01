package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Singleton settings row — always id=1, upserted in place. No @GeneratedValue:
// the id is assigned explicitly by MaintenanceService so there is only ever
// one row, rather than modeling this as a full CRUD table.
@Entity
@Table(name = "maintenance_mode")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceMode {

    @Id
    private Long id;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000, nullable = false)
    private String description;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
