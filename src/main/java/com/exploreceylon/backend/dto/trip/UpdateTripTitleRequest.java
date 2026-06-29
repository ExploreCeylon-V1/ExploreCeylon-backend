package com.exploreceylon.backend.dto.trip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTripTitleRequest {
    
    @NotBlank(message = "Title cannot be empty")
    @Size(max = 200, message = "Title too long")
    private String title;
}