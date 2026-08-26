package com.exploreceylon.backend.dto.verification;

import com.exploreceylon.backend.model.User.KycStatus;
import com.exploreceylon.backend.model.UserVerification.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusResponse {
    private KycStatus status;
    private UUID verificationId;
    private String nationality;
    private DocumentType documentType;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private boolean canSubmit;
}
