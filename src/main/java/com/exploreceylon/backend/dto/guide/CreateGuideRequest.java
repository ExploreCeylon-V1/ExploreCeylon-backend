package com.exploreceylon.backend.dto.guide;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateGuideRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String bio;
    private String photoUrl;

    @NotBlank(message = "Languages is required")
    private String languages;

    @NotBlank(message = "Specialties is required")
    private String specialties;

    @NotBlank(message = "District is required")
    private String district;

    @NotNull(message = "Price per day is required")
    private Double pricePerDay;

    private String phone;
    private String whatsappNumber;
    private String email;
    private List<String> imageUrls;
}