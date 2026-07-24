package com.exploreceylon.backend.service;

import com.exploreceylon.backend.config.JwtService;
import com.exploreceylon.backend.dto.auth.AuthResponse;
import com.exploreceylon.backend.dto.auth.LoginRequest;
import com.exploreceylon.backend.dto.auth.RegisterRequest;
import com.exploreceylon.backend.dto.auth.GoogleLoginRequest;
import com.exploreceylon.backend.exception.RateLimitException;
import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.RefreshToken;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.VerificationCode.CodeType;
import com.exploreceylon.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final LoginHistoryService loginHistoryService;
    private final VerificationService verificationService;
    private final RestTemplate restTemplate;

    public AuthResponse register(RegisterRequest request, String ipAddress, String deviceInfo) {
        log.info("Registering new user: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // Public self-registration always creates a TRAVELER — never trust a client-supplied
        // role here. Admin accounts are only created by an existing admin promoting a user
        // via PUT /api/v1/admin/users/{id}/role (AdminController), which is hasRole("ADMIN")-gated.
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.TRAVELER)
                .authProvider(User.AuthProvider.LOCAL)
                .nationality(request.getNationality())
                .language(request.getLanguage() != null ? request.getLanguage() : "en")
                .phone(request.getPhone())
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", user.getEmail());

        return buildAuthResponse(user, LoginHistory.LoginType.LOCAL, ipAddress, deviceInfo);
    }

    public AuthResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        log.info("Login attempt: {}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Login successful: {}", user.getEmail());

        loginHistoryService.record(user, LoginHistory.LoginType.LOCAL, ipAddress, deviceInfo);
        return buildAuthResponse(user, LoginHistory.LoginType.LOCAL, ipAddress, deviceInfo);
    }

    // ─── Google Login / Account Linking ───
    // email not found          -> create new GOOGLE user
    // email found, no googleId -> link this Google account to the existing user (keeps
    //                             their current authProvider/password untouched)
    // email found, googleId set-> normal login, no duplicate created
    public AuthResponse googleLogin(GoogleLoginRequest request, String ipAddress, String deviceInfo) {
        log.info("Google login attempt");

        String googleUserInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(request.getToken());
        HttpEntity<String> entity = new HttpEntity<>("", headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(googleUserInfoUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> userInfo = response.getBody();

            if (userInfo == null || !userInfo.containsKey("email")) {
                throw new RuntimeException("Failed to retrieve user info from Google");
            }

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String picture = (String) userInfo.get("picture");
            String googleId = (String) userInfo.get("sub");

            User user = userRepository.findByEmail(email).map(existing -> {
                if (existing.getGoogleId() == null) {
                    log.info("Linking Google account to existing {} user: {}", existing.getAuthProvider(), email);
                    existing.setGoogleId(googleId);
                    userRepository.save(existing);
                }
                return existing;
            }).orElseGet(() -> {
                log.info("Creating new user from Google Login: {}", email);
                User newUser = User.builder()
                        .name(name)
                        .email(email)
                        // No password yet — Google users set one later via "Create Password".
                        .googleId(googleId)
                        .authProvider(User.AuthProvider.GOOGLE)
                        .role(User.Role.TRAVELER)
                        .profilePhoto(picture)
                        .language("en")
                        .emailVerified(true) // Google already verified this address
                        .build();
                return userRepository.save(newUser);
            });

            if (!user.isEnabled()) {
                throw new RuntimeException("This account has been deactivated");
            }

            log.info("Google Login successful: {}", email);

            loginHistoryService.record(user, LoginHistory.LoginType.GOOGLE, ipAddress, deviceInfo);
            return buildAuthResponse(user, LoginHistory.LoginType.GOOGLE, ipAddress, deviceInfo);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new RuntimeException("Invalid Google token. Sign in failed.");
        }
    }

    // ─── Refresh / Logout ───
    public AuthResponse refreshAccessToken(String token) {
        RefreshToken refreshToken = refreshTokenService.verifyAndGet(token);
        User user = refreshToken.getUser();

        String accessToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    // ─── Forgot Password / Reset ───
    // Never reveals whether the email exists: an unknown email silently no-ops, and
    // a rate-limit hit on a known email is swallowed too (surfacing it would leak
    // existence via timing/status-code differences). Works identically for LOCAL and
    // GOOGLE-without-password users — resetPassword() never touches authProvider.
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                verificationService.generateAndSend(user, CodeType.PASSWORD_RESET);
                log.info("Password reset code requested for {}", email);
            } catch (RateLimitException e) {
                log.warn("Password reset request rate-limited for {}", email);
            }
        });
    }

    // Pre-check only — does NOT consume the code, so the same code can still be
    // spent by resetPassword() right after. Returns false uniformly for an unknown
    // email or a wrong/expired/used code, so neither leaks which one it was.
    public boolean verifyResetCode(String email, String code) {
        return userRepository.findByEmail(email)
                .map(user -> verificationService.checkOnly(user, CodeType.PASSWORD_RESET, code))
                .orElse(false);
    }

    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid or expired code"));

        boolean valid = verificationService.verify(user, CodeType.PASSWORD_RESET, code);
        if (!valid) {
            throw new RuntimeException("Invalid or expired code");
        }

        User managed = userRepository.findById(user.getId()).orElseThrow();
        managed.setPassword(passwordEncoder.encode(newPassword)); // authProvider left untouched
        userRepository.save(managed);

        refreshTokenService.revokeAllForUser(managed); // force re-login everywhere
        log.info("Password reset completed for {}", email);
    }

    // ─── Shared helpers ───
    private AuthResponse buildAuthResponse(User user, LoginHistory.LoginType loginType,
                                            String ipAddress, String deviceInfo) {
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, loginType, ipAddress, deviceInfo);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
