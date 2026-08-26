package com.exploreceylon.backend.dto.verification;

import com.exploreceylon.backend.model.UserVerification.DocumentType;
import com.exploreceylon.backend.model.UserVerification.VerificationStatus;
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
public class AdminVerificationResponse {
    private UUID id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String userProfilePhoto;
    private String nationality;
    private DocumentType documentType;
    private boolean hasBackImage;
    private VerificationStatus status;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Long reviewedById;
    private String reviewedByName;
    private String reviewedByEmail;
}
