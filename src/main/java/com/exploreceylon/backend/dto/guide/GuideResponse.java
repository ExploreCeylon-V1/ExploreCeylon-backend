package com.exploreceylon.backend.dto.guide;

import lombok.Data;
import java.util.List;

@Data
public class GuideResponse {
    private Long id;
    private String fullName;
    private String bio;
    private String photoUrl;
    private String languages;
    private String specialties;
    private String district;
    private Double pricePerDay;
    private Boolean verified;
    private Boolean available;
    private Double rating;
    private Integer reviewCount;
    private String phone;
    private String email;
    private List<String> imageUrls;
}
