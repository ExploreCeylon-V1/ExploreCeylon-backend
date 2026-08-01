package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.auth.AuthResponse;
import com.exploreceylon.backend.dto.auth.ForgotPasswordRequest;
import com.exploreceylon.backend.dto.auth.LoginRequest;
import com.exploreceylon.backend.dto.auth.RefreshTokenRequest;
import com.exploreceylon.backend.dto.auth.RegisterRequest;
import com.exploreceylon.backend.dto.auth.ResetPasswordRequest;
import com.exploreceylon.backend.dto.auth.GoogleLoginRequest;
import com.exploreceylon.backend.dto.auth.VerifyResetCodeRequest;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.service.AuthService;
import com.exploreceylon.backend.util.DeviceInfoUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // POST /api/v1/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.register(request,
                DeviceInfoUtils.clientIp(httpRequest),
                DeviceInfoUtils.describe(httpRequest.getHeader("User-Agent"))));
    }

    // POST /api/v1/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request,
                DeviceInfoUtils.clientIp(httpRequest),
                DeviceInfoUtils.describe(httpRequest.getHeader("User-Agent"))));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.googleLogin(request,
                DeviceInfoUtils.clientIp(httpRequest),
                DeviceInfoUtils.describe(httpRequest.getHeader("User-Agent"))));
    }

    // POST /api/v1/auth/refresh-token — exchange a valid refresh token for a new access token
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request.getRefreshToken()));
    }

    // POST /api/v1/auth/logout — revoke a single refresh token (this device/session only)
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken is required"));
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // POST /api/v1/auth/forgot-password — always returns the same generic message,
    // whether or not the email exists (see AuthService.forgotPassword).
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Password reset instructions sent if the email exists."));
    }

    // POST /api/v1/auth/verify-reset-code — pre-check only, does not consume the code
    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        boolean valid = authService.verifyResetCode(request.getEmail(), request.getCode());
        if (!valid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired code"));
        }
        return ResponseEntity.ok(Map.of("message", "Code verified"));
    }

    // POST /api/v1/auth/reset-password — consumes the code, sets the new password,
    // and revokes every refresh token for the account (force re-login everywhere).
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
        }
        authService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    // GET /api/v1/auth/me (protected)
    @GetMapping("/me")
    public ResponseEntity<String> me() {
        return ResponseEntity.ok("You are authenticated!");
    }

    // POST /api/v1/auth/change-password
    // Google-only users (no password yet) skip the currentPassword check — this
    // doubles as the "Create Password" flow. Never touches authProvider.
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> body) {

        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "newPassword is required"));
        }

        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "New password must be at least 8 characters"));
        }

        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Passwords do not match"));
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean hasPassword = user.getPassword() != null;

        if (hasPassword) {
            if (currentPassword == null || currentPassword.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Current password is required"));
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Current password is incorrect"));
            }
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        String message = hasPassword ? "Password updated successfully" : "Password created successfully";
        return ResponseEntity.ok(Map.of("message", message));
    }
}