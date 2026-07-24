package com.exploreceylon.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {

    @NotBlank(message = "Message cannot be empty")
    private String content;
}
