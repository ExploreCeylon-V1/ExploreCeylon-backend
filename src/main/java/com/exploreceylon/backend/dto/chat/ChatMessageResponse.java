package com.exploreceylon.backend.dto.chat;

import com.exploreceylon.backend.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private ChatMessage.SenderRole senderRole;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
