package com.exploreceylon.backend.dto.notification;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String bookingType;
    private Long bookingId;
    private boolean read;
    private LocalDateTime createdAt;
}
