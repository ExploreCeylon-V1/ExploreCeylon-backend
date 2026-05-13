package com.exploreceylon.backend.dto.budget;

import com.exploreceylon.backend.model.BudgetItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AddBudgetItemRequest {

    @NotNull(message = "Category is required")
    private BudgetItem.ItemCategory category;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Amount is required")
    private Double amount;

    private String currency;
    private LocalDate date;
    private String notes;
    private String referenceId;
}
