package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.PageResponse;
import com.exploreceylon.backend.dto.verification.AdminVerificationResponse;
import com.exploreceylon.backend.dto.verification.SignedUrlResponse;
import com.exploreceylon.backend.dto.verification.VerificationStatusResponse;
import com.exploreceylon.backend.exception.KycVerificationException;
import com.exploreceylon.backend.exception.ResourceNotFoundException;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.User.KycStatus;
import com.exploreceylon.backend.model.UserVerification;
import com.exploreceylon.backend.model.UserVerification.DocumentType;
import com.exploreceylon.backend.model.UserVerification.VerificationStatus;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.repository.UserVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.exploreceylon.backend.specification.UserVerificationSpecifications;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserVerificationService {

    private final UserVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final EmailSenderService emailSenderService;

    /**
     * Submit ID verification documents.
     * Enforces:
     * - APPROVED is terminal: block re-submission if user is already APPROVED.
     * - No duplicate PENDING submissions.
     * - Server-side validation: Sri Lankan nationality requires NIC or DRIVING_LICENSE (front + back).
     * - Other nationality requires PASSPORT (front only).
     */
    @Transactional
    public VerificationStatusResponse submitVerification(
            User currentUser,
            String nationality,
            DocumentType documentType,
            MultipartFile frontImage,
            MultipartFile backImage) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. APPROVED is terminal
        if (user.getKycStatus() == KycStatus.APPROVED) {
            throw new IllegalArgumentException("Identity is already verified. Re-submission is not permitted.");
        }

        // 2. Prevent concurrent / duplicate PENDING submissions
        if (user.getKycStatus() == KycStatus.PENDING ||
                verificationRepository.existsByUserIdAndStatus(user.getId(), VerificationStatus.PENDING)) {
            throw new IllegalArgumentException("A verification submission is already under review.");
        }

        // 3. Validate nationality
        if (nationality == null || nationality.trim().isBlank()) {
            throw new IllegalArgumentException("Nationality is required.");
        }
        String normNationality = nationality.trim();
        boolean isSriLankan = normNationality.equalsIgnoreCase("Sri Lankan") || normNationality.equalsIgnoreCase("Sri Lanka");

        if (documentType == null) {
            throw new IllegalArgumentException("Document type is required.");
        }

        // 4. Strict server-side nationality vs documentType consistency
        if (isSriLankan) {
            if (documentType != DocumentType.NIC && documentType != DocumentType.DRIVING_LICENSE) {
                throw new IllegalArgumentException("Sri Lankan citizens must provide either NIC or DRIVING_LICENSE.");
            }
            if (frontImage == null || frontImage.isEmpty()) {
                throw new IllegalArgumentException("Front image of document is required.");
            }
            if (backImage == null || backImage.isEmpty()) {
                throw new IllegalArgumentException("Back image of document is required for NIC and Driving License.");
            }
        } else {
            if (documentType != DocumentType.PASSPORT) {
                throw new IllegalArgumentException("Foreign nationals must provide a PASSPORT.");
            }
            if (frontImage == null || frontImage.isEmpty()) {
                throw new IllegalArgumentException("Passport photo page image is required.");
            }
        }

        UUID verificationId = UUID.randomUUID();

        // 5. Upload files to S3 under private kyc-documents/ prefix
        String frontKey = s3Service.uploadKycDocument(frontImage, user.getId(), verificationId, "front");
        String backKey = (backImage != null && !backImage.isEmpty())
                ? s3Service.uploadKycDocument(backImage, user.getId(), verificationId, "back")
                : null;

        // 6. Save UserVerification record
        UserVerification verification = UserVerification.builder()
                .id(verificationId)
                .user(user)
                .nationality(normNationality)
                .documentType(documentType)
                .frontImageUrl(frontKey)
                .backImageUrl(backKey)
                .status(VerificationStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();

        UserVerification saved = verificationRepository.save(verification);

        // 7. Update User's nationality & kycStatus
        user.setNationality(normNationality);
        user.setKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        log.info("KYC verification submitted: userId={}, verificationId={}, docType={}",
                user.getId(), verificationId, documentType);

        return toStatusResponse(user.getKycStatus(), saved);
    }

    /**
     * Get latest verification status for the current user.
     */
    @Transactional(readOnly = true)
    public VerificationStatusResponse getStatus(User currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        KycStatus status = user.getKycStatus() != null ? user.getKycStatus() : KycStatus.NOT_SUBMITTED;
        Optional<UserVerification> latest = verificationRepository
                .findFirstByUserIdOrderBySubmittedAtDesc(user.getId());

        if (latest.isEmpty()) {
            return VerificationStatusResponse.builder()
                    .status(status)
                    .nationality(user.getNationality())
                    .canSubmit(status != KycStatus.APPROVED && status != KycStatus.PENDING)
                    .build();
        }

        return toStatusResponse(status, latest.get());
    }

    /**
     * Admin: get paginated, filtered list of verification submissions.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminVerificationResponse> getAdminVerifications(
            String statusStr,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        VerificationStatus status = null;
        if (statusStr != null && !statusStr.isBlank() && !statusStr.equalsIgnoreCase("ALL")) {
            try {
                status = VerificationStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        String sortProperty = (sortBy != null && !sortBy.isBlank()) ? sortBy : "submittedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));

        Specification<UserVerification> spec = UserVerificationSpecifications.withFilters(status, search);
        Page<UserVerification> resultPage = verificationRepository.findAll(spec, pageable);

        var content = resultPage.getContent().stream()
                .map(this::toAdminResponse)
                .toList();

        return PageResponse.<AdminVerificationResponse>builder()
                .content(content)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    /**
     * Admin: generate a short-lived (10 min) signed S3 URL for front or back document image.
     */
    @Transactional(readOnly = true)
    public SignedUrlResponse getSignedImageUrl(UUID verificationId, String side) {
        UserVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification submission not found"));

        String key;
        if ("front".equalsIgnoreCase(side)) {
            key = verification.getFrontImageUrl();
        } else if ("back".equalsIgnoreCase(side)) {
            key = verification.getBackImageUrl();
            if (key == null) {
                throw new ResourceNotFoundException("No back document image exists for this submission");
            }
        } else {
            throw new IllegalArgumentException("Side must be 'front' or 'back'");
        }

        String presignedUrl = s3Service.generatePresignedGetUrl(key, Duration.ofMinutes(10));
        return new SignedUrlResponse(presignedUrl);
    }

    /**
     * Admin: approve verification submission.
     */
    @Transactional
    public AdminVerificationResponse approveVerification(UUID verificationId, User admin) {
        UserVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification submission not found"));

        verification.setStatus(VerificationStatus.APPROVED);
        verification.setReviewedAt(LocalDateTime.now());
        verification.setReviewedBy(admin);
        verification.setRejectionReason(null);
        UserVerification saved = verificationRepository.save(verification);

        User user = verification.getUser();
        user.setKycStatus(KycStatus.APPROVED);
        userRepository.save(user);

        log.info("KYC approved: verificationId={}, userId={}, adminId={}",
                verificationId, user.getId(), admin.getId());

        try {
            emailSenderService.sendKycApproved(user.getEmail(), user.getName());
        } catch (Exception e) {
            log.error("Failed to send KYC approval notification email to {}", user.getEmail(), e);
        }

        return toAdminResponse(saved);
    }

    /**
     * Admin: reject verification submission with non-empty reason.
     */
    @Transactional
    public AdminVerificationResponse rejectVerification(UUID verificationId, String reason, User admin) {
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required and cannot be empty.");
        }

        UserVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification submission not found"));

        String cleanReason = reason.trim();
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setRejectionReason(cleanReason);
        verification.setReviewedAt(LocalDateTime.now());
        verification.setReviewedBy(admin);
        UserVerification saved = verificationRepository.save(verification);

        User user = verification.getUser();
        user.setKycStatus(KycStatus.REJECTED);
        userRepository.save(user);

        log.info("KYC rejected: verificationId={}, userId={}, adminId={}, reason={}",
                verificationId, user.getId(), admin.getId(), cleanReason);

        try {
            emailSenderService.sendKycRejected(user.getEmail(), user.getName(), cleanReason);
        } catch (Exception e) {
            log.error("Failed to send KYC rejection notification email to {}", user.getEmail(), e);
        }

        return toAdminResponse(saved);
    }

    /**
     * Server-side gate check for booking creation (Guides & Vehicles).
     * Throws {@link KycVerificationException} (mapped to HTTP 403) if user is not APPROVED.
     */
    @Transactional(readOnly = true)
    public void assertApprovedForBooking(User user) {
        KycStatus status = user.getKycStatus() != null ? user.getKycStatus() : KycStatus.NOT_SUBMITTED;

        if (status == KycStatus.APPROVED) {
            return;
        }

        if (status == KycStatus.PENDING) {
            throw new KycVerificationException(
                    "VERIFICATION_PENDING",
                    "Your identity verification is currently under review. Bookings will unlock once an administrator approves your ID."
            );
        }

        if (status == KycStatus.REJECTED) {
            String reason = verificationRepository.findFirstByUserIdOrderBySubmittedAtDesc(user.getId())
                    .map(UserVerification::getRejectionReason)
                    .orElse("Your document did not meet the verification requirements.");
            throw new KycVerificationException(
                    "VERIFICATION_REJECTED",
                    "Your identity verification was rejected. Please re-submit your ID documents to proceed with booking.",
                    reason
            );
        }

        throw new KycVerificationException(
                "VERIFICATION_REQUIRED",
                "Identity verification is required before you can book vehicles or tour guides. Please submit your government-issued ID."
        );
    }

    private VerificationStatusResponse toStatusResponse(KycStatus kycStatus, UserVerification verification) {
        return VerificationStatusResponse.builder()
                .status(kycStatus)
                .verificationId(verification.getId())
                .nationality(verification.getNationality())
                .documentType(verification.getDocumentType())
                .rejectionReason(verification.getRejectionReason())
                .submittedAt(verification.getSubmittedAt())
                .reviewedAt(verification.getReviewedAt())
                .canSubmit(kycStatus != KycStatus.APPROVED && kycStatus != KycStatus.PENDING)
                .build();
    }

    private AdminVerificationResponse toAdminResponse(UserVerification v) {
        User u = v.getUser();
        User admin = v.getReviewedBy();

        return AdminVerificationResponse.builder()
                .id(v.getId())
                .userId(u.getId())
                .userName(u.getName())
                .userEmail(u.getEmail())
                .userPhone(u.getPhone())
                .userProfilePhoto(u.getProfilePhoto())
                .nationality(v.getNationality())
                .documentType(v.getDocumentType())
                .hasBackImage(v.getBackImageUrl() != null && !v.getBackImageUrl().isBlank())
                .status(v.getStatus())
                .rejectionReason(v.getRejectionReason())
                .submittedAt(v.getSubmittedAt())
                .reviewedAt(v.getReviewedAt())
                .reviewedById(admin != null ? admin.getId() : null)
                .reviewedByName(admin != null ? admin.getName() : null)
                .reviewedByEmail(admin != null ? admin.getEmail() : null)
                .build();
    }
}
