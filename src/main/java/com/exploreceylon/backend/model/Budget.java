package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@ToString(exclude = {"trip", "user", "items"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Double totalBudget;

    @Builder.Default
    private String currency = "USD";

    @OneToMany(mappedBy = "budget",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    @Builder.Default
    private List<BudgetItem> items = new ArrayList<>();

    // Per-category spending allocations (e.g. HOTEL → 350.0). Optional:
    // an empty map means the traveler hasn't split the budget by category.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "budget_category_allocations",
        joinColumns = @JoinColumn(name = "budget_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "category")
    @Column(name = "amount")
    @Builder.Default
    private Map<BudgetItem.ItemCategory, Double> categoryBudgets = new HashMap<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
