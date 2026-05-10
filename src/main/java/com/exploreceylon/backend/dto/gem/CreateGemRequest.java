package com.exploreceylon.backend.dto.gem;

import com.exploreceylon.backend.model.HiddenGem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateGemRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Category is required")
    private HiddenGem.GemCategory category;

    @NotBlank(message = "District is required")
    private String district;

    private Double latitude;
    private Double longitude;
    private String howToGetThere;
    private String bestTime;
    private String tips;
    private List<String> imageUrls;
}