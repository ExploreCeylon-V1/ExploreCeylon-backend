package com.exploreceylon.backend.dto.verification;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectVerificationRequest {

    @NotBlank(message = "Rejection reason is required and cannot be empty")
    private String reason;
}
