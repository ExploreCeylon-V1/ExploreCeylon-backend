package com.exploreceylon.backend.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPaymentNotificationRequest {

    @Size(max = 500, message = "Notification message cannot exceed 500 characters")
    private String message;
}
