package com.exploreceylon.backend.dto.review;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Reviewresponse {
    private Long id;
    private Long gemId;
    private String travelerName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}