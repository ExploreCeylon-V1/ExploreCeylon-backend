package com.exploreceylon.backend.dto.budget;

import com.exploreceylon.backend.model.BudgetItem;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BudgetItemResponse {
    private Long id;
    private BudgetItem.ItemCategory category;
    private String title;
    private Double amount;
    private String currency;
    private LocalDate date;
    private Boolean autoAdded;
    private String notes;
    private String referenceId;
    private LocalDateTime createdAt;
}