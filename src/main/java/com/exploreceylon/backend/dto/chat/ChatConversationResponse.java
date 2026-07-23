package com.exploreceylon.backend.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationResponse {
    private Long id;
    private Long travelerId;
    private String travelerName;
    private String travelerEmail;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Boolean unreadByAdmin;
    private Boolean unreadByTraveler;
    private LocalDateTime createdAt;
}
