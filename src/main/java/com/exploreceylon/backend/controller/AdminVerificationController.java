package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.admin.PageResponse;
import com.exploreceylon.backend.dto.verification.AdminVerificationResponse;
import com.exploreceylon.backend.dto.verification.RejectVerificationRequest;
import com.exploreceylon.backend.dto.verification.SignedUrlResponse;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.service.UserVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/verification", "/api/admin/verification"})
@RequiredArgsConstructor
public class AdminVerificationController {

    private final UserVerificationService verificationService;

    // GET /api/v1/admin/verification
    @GetMapping
    public ResponseEntity<PageResponse<AdminVerificationResponse>> getAllVerifications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "15") int size,
            @RequestParam(required = false, defaultValue = "submittedAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {

        return ResponseEntity.ok(verificationService.getAdminVerifications(
                status, search, page, size, sortBy, sortDir));
    }

    // GET /api/v1/admin/verification/{id}/image/{side}
    @GetMapping("/{id}/image/{side}")
    public ResponseEntity<SignedUrlResponse> getImageSignedUrl(
            @PathVariable UUID id,
            @PathVariable String side) {

        return ResponseEntity.ok(verificationService.getSignedImageUrl(id, side));
    }

    // POST /api/v1/admin/verification/{id}/approve
    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminVerificationResponse> approveVerification(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {

        return ResponseEntity.ok(verificationService.approveVerification(id, admin));
    }

    // POST /api/v1/admin/verification/{id}/reject
    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminVerificationResponse> rejectVerification(
            @PathVariable UUID id,
            @Valid @RequestBody RejectVerificationRequest request,
            @AuthenticationPrincipal User admin) {

        return ResponseEntity.ok(verificationService.rejectVerification(id, request.getReason(), admin));
    }
}
