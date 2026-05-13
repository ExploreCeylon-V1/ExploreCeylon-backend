package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetItemRepository
        extends JpaRepository<BudgetItem, Long> {

    List<BudgetItem> findByBudgetIdOrderByCreatedAtDesc(Long budgetId);

    List<BudgetItem> findByBudgetIdAndCategoryOrderByCreatedAtDesc(
            Long budgetId, BudgetItem.ItemCategory category);

    // Total spent
    @Query("SELECT COALESCE(SUM(i.amount), 0) " +
           "FROM BudgetItem i WHERE i.budget.id = :budgetId")
    Double getTotalSpent(@Param("budgetId") Long budgetId);

    // Total by category
    @Query("SELECT COALESCE(SUM(i.amount), 0) " +
           "FROM BudgetItem i WHERE " +
           "i.budget.id = :budgetId AND i.category = :category")
    Double getTotalByCategory(
            @Param("budgetId")  Long budgetId,
            @Param("category")  BudgetItem.ItemCategory category);

    // Check duplicate auto-added
    boolean existsByBudgetIdAndReferenceId(
            Long budgetId, String referenceId);
}