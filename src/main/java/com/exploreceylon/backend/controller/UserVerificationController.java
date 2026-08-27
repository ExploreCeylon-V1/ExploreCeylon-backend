package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.verification.VerificationStatusResponse;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.UserVerification.DocumentType;
import com.exploreceylon.backend.service.UserVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/v1/verification", "/api/verification"})
@RequiredArgsConstructor
public class UserVerificationController {

    private final UserVerificationService verificationService;

    // POST /api/v1/verification/submit (and /api/verification/submit)
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationStatusResponse> submitVerification(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("nationality") String nationality,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam(value = "backImage", required = false) MultipartFile backImage) {

        VerificationStatusResponse response = verificationService.submitVerification(
                currentUser, nationality, documentType, frontImage, backImage);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/verification/status (and /api/verification/status)
    @GetMapping("/status")
    public ResponseEntity<VerificationStatusResponse> getStatus(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(verificationService.getStatus(currentUser));
    }
}
