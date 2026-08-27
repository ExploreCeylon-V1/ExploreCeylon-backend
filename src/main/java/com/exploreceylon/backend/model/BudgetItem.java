package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "budget_items")
@Getter
@Setter
@ToString(exclude = "budget")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCategory category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Double amount;

    @Builder.Default
    private String currency = "USD";

    private LocalDate date;

    @Builder.Default
    private Boolean autoAdded = false;

    @Column(length = 500)
    private String notes;

    private String referenceId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ItemCategory {
        HOTEL, VEHICLE, GUIDE,
        FOOD, ACTIVITY, TRANSPORT, MISC
    }
}
