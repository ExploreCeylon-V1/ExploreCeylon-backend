package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.model.RefreshToken;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.VerificationCode.CodeType;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.service.RefreshTokenService;
import com.exploreceylon.backend.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final RefreshTokenService refreshTokenService;

    // ── Shared user JSON (null-safe; not using Map.of due to null + 10-pair cap) ──
    private Map<String, Object> userJson(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",            u.getId());
        m.put("name",          u.getName());
        m.put("email",         u.getEmail());
        m.put("phone",         u.getPhone() != null ? u.getPhone() : "");
        m.put("profilePhoto",  u.getProfilePhoto() != null ? u.getProfilePhoto() : "");
        m.put("role",          u.getRole().name());
        m.put("nationality",   u.getNationality() != null ? u.getNationality() : "");
        m.put("language",      u.getLanguage() != null ? u.getLanguage() : "");
        m.put("emailVerified", Boolean.TRUE.equals(u.getEmailVerified()));
        m.put("phoneVerified", Boolean.TRUE.equals(u.getPhoneVerified()));
        m.put("hasPassword",   u.getPassword() != null);
        m.put("authProvider",  u.getAuthProvider() != null ? u.getAuthProvider().name() : "LOCAL");
        return m;
    }

    // GET /api/v1/users/count — public total registered user count (homepage stats card)
    @GetMapping("/count")
    public ResponseEntity<?> getUserCount() {
        return ResponseEntity.ok(Map.of("count", userRepository.count()));
    }

    // GET /api/v1/users/me
    @GetMapping("/me")
    public ResponseEntity<?> getMe(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userJson(user));
    }

    // POST /api/v1/users/me/photo  — upload profile photo
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProfilePhoto(
            @AuthenticationPrincipal User currentUser,
            @RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            String dataUrl = "data:" + contentType + ";base64," + base64;
            user.setProfilePhoto(dataUrl);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("profilePhoto", dataUrl));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to store profile photo"));
        }
    }

    // DELETE /api/v1/users/me/photo  — remove profile photo
    @DeleteMapping("/me/photo")
    public ResponseEntity<?> deleteProfilePhoto(
            @AuthenticationPrincipal User currentUser) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setProfilePhoto(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profile photo removed"));
    }

    // PUT /api/v1/users/me  — update name, phone, nationality, language
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> body) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (body.containsKey("name") && !body.get("name").isBlank()) {
            user.setName(body.get("name"));
        }
        if (body.containsKey("email")) {
            String newEmail = body.get("email");
            // Changing the address invalidates a prior verification.
            if (newEmail != null && !newEmail.isBlank()
                    && !newEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
                }
                user.setEmail(newEmail);
                user.setEmailVerified(false);
                verificationService.resetChannel(user.getId(), CodeType.EMAIL);
            }
        }
        if (body.containsKey("phone")) {
            String newPhone = body.get("phone");
            // Changing the number invalidates a prior verification.
            if (newPhone == null ? user.getPhone() != null : !newPhone.equals(user.getPhone())) {
                user.setPhoneVerified(false);
                verificationService.resetChannel(user.getId(), CodeType.PHONE);
            }
            user.setPhone(newPhone);
        }
        if (body.containsKey("nationality")) {
            user.setNationality(body.get("nationality"));
        }
        if (body.containsKey("language")) {
            user.setLanguage(body.get("language"));
        }

        userRepository.save(user);
        return ResponseEntity.ok(userJson(user));
    }

    // POST /api/v1/users/me/deactivate  — soft-delete (blocks future logins via isEnabled())
    @PostMapping("/me/deactivate")
    public ResponseEntity<?> deactivate(@AuthenticationPrincipal User currentUser) {
        return deactivateAccount(currentUser);
    }

    // DELETE /api/v1/users/account  — same soft-delete, REST-style alias
    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal User currentUser) {
        return deactivateAccount(currentUser);
    }

    private ResponseEntity<?> deactivateAccount(User currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user); // kill every active session too
        return ResponseEntity.ok(Map.of("message", "Account deactivated"));
    }

    // ── Sessions ─────────────────────────────────────────────
    // GET /api/v1/users/sessions — active (non-revoked, non-expired) refresh tokens = devices
    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(@AuthenticationPrincipal User currentUser) {
        List<Map<String, Object>> sessions = refreshTokenService.getActiveSessions(currentUser).stream()
                .map(this::sessionJson)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    private Map<String, Object> sessionJson(RefreshToken rt) {
        Map<String, Object> m = new HashMap<>();
        m.put("device",    rt.getDeviceInfo() != null ? rt.getDeviceInfo() : "Unknown Device");
        m.put("loginType", rt.getLoginType() != null ? rt.getLoginType().name() : "LOCAL");
        m.put("lastActive", rt.getCreatedAt());
        return m;
    }

    // DELETE /api/v1/users/sessions/logout-all — revoke every refresh token for this user
    @DeleteMapping("/sessions/logout-all")
    public ResponseEntity<?> logoutAllSessions(@AuthenticationPrincipal User currentUser) {
        refreshTokenService.revokeAllForUser(currentUser);
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    // ── Phone OTP ──────────────────────────────────────────
    // POST /api/v1/users/me/phone/send-otp
    @PostMapping("/me/phone/send-otp")
    public ResponseEntity<?> sendPhoneOtp(@AuthenticationPrincipal User user) {
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Add a phone number first"));
        }
        String devCode = verificationService.generateAndSend(user, CodeType.PHONE);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "OTP sent to " + user.getPhone());
        if (devCode != null) resp.put("devCode", devCode);
        return ResponseEntity.ok(resp);
    }

    // POST /api/v1/users/me/phone/verify-otp  { code }
    @PostMapping("/me/phone/verify-otp")
    public ResponseEntity<?> verifyPhoneOtp(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        boolean ok = verificationService.verify(user, CodeType.PHONE, body.get("code"));
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired code"));
        return ResponseEntity.ok(Map.of("phoneVerified", true));
    }

    // ── Email verification ─────────────────────────────────
    // POST /api/v1/users/me/email/send-verification
    @PostMapping("/me/email/send-verification")
    public ResponseEntity<?> sendEmailVerification(@AuthenticationPrincipal User user) {
        String devCode = verificationService.generateAndSend(user, CodeType.EMAIL);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Verification code sent to " + user.getEmail());
        if (devCode != null) resp.put("devCode", devCode);
        return ResponseEntity.ok(resp);
    }

    // POST /api/v1/users/me/email/verify  { code }
    @PostMapping("/me/email/verify")
    public ResponseEntity<?> verifyEmail(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        boolean ok = verificationService.verify(user, CodeType.EMAIL, body.get("code"));
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired code"));
        return ResponseEntity.ok(Map.of("emailVerified", true));
    }
}