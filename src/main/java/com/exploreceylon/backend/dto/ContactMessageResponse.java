package com.exploreceylon.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class ContactMessageResponse {
    private Long id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private Boolean isRead;
    private String adminReply;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
}