package com.exploreceylon.backend;

import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.RefreshToken;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.VerificationCode;
import com.exploreceylon.backend.model.VerificationCode.CodeType;
import com.exploreceylon.backend.repository.LoginHistoryRepository;
import com.exploreceylon.backend.repository.RefreshTokenRepository;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.repository.VerificationCodeRepository;
import com.exploreceylon.backend.repository.VerificationRateLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end coverage for the forgot-password / reset-password flow. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForgotPasswordE2ETest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired VerificationCodeRepository codes;
    @Autowired VerificationRateLimitRepository rateLimits;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginHistoryRepository loginHistoryRepo;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String FORGOT  = "/api/v1/auth/forgot-password";
    private static final String VERIFY  = "/api/v1/auth/verify-reset-code";
    private static final String RESET   = "/api/v1/auth/reset-password";

    private User localUser;

    @BeforeEach
    void setUp() {
        refreshTokens.deleteAll();
        loginHistoryRepo.deleteAll();
        rateLimits.deleteAll();
        codes.deleteAll();
        users.deleteAll();
        localUser = users.save(User.builder()
                .name("Local Traveler")
                .email("local@example.com")
                .password(passwordEncoder.encode("OldPass123"))
                .role(User.Role.TRAVELER)
                .authProvider(User.AuthProvider.LOCAL)
                .build());
    }

    private String codeFor(User user) {
        return codes.findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(user.getId(), CodeType.PASSWORD_RESET)
                .orElseThrow().getCode();
    }

    // ── 1. existing LOCAL user requests reset successfully ──
    @Test
    void forgotPassword_existingUser_generatesCode() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("if the email exists")));

        assertTrue(codes.findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(
                localUser.getId(), CodeType.PASSWORD_RESET).isPresent());
    }

    // ── 2. unknown email returns the same generic response, no code created ──
    @Test
    void forgotPassword_unknownEmail_isSafeAndGeneric() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("if the email exists")));

        assertEquals(0, codes.count());
    }

    // ── 3. correct OTP verification succeeds ─────────────────
    @Test
    void verifyResetCode_correctCode_succeeds() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));
        String code = codeFor(localUser);

        mvc.perform(post(VERIFY).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        // still unconsumed — verify-reset-code is a pre-check, reset-password spends it
        assertTrue(codes.findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(
                localUser.getId(), CodeType.PASSWORD_RESET).isPresent());
    }

    // ── 4. wrong OTP rejected ─────────────────────────────────
    @Test
    void verifyResetCode_wrongCode_rejected() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));

        mvc.perform(post(VERIFY).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Invalid")));
    }

    // ── 5. expired OTP rejected ───────────────────────────────
    @Test
    void verifyResetCode_expiredCode_rejected() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));
        VerificationCode vc = codes.findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(
                localUser.getId(), CodeType.PASSWORD_RESET).orElseThrow();
        String code = vc.getCode();
        vc.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        codes.save(vc);

        mvc.perform(post(VERIFY).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Invalid")));
    }

    // ── 6 & 7. reset updates the password, stored as a BCrypt hash ──
    @Test
    void resetPassword_success_updatesEncryptedPassword() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));
        String code = codeFor(localUser);

        mvc.perform(post(RESET).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\","
                                + "\"newPassword\":\"BrandNewPass1\",\"confirmPassword\":\"BrandNewPass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("successfully")));

        User reloaded = users.findById(localUser.getId()).orElseThrow();
        assertNotEquals("BrandNewPass1", reloaded.getPassword());
        assertTrue(reloaded.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("BrandNewPass1", reloaded.getPassword()));
    }

    // used code cannot be replayed for a second reset
    @Test
    void resetPassword_usedCode_rejectedOnReplay() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));
        String code = codeFor(localUser);

        mvc.perform(post(RESET).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\","
                                + "\"newPassword\":\"FirstPass12\",\"confirmPassword\":\"FirstPass12\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(RESET).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\","
                                + "\"newPassword\":\"SecondPass2\",\"confirmPassword\":\"SecondPass2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Invalid")));
    }

    // ── 8. Google user without a password can create one via reset flow ──
    @Test
    void resetPassword_googleUserWithoutPassword_createsPassword() throws Exception {
        User googleUser = users.save(User.builder()
                .name("Google Traveler")
                .email("google@example.com")
                .googleId("google-sub-1")
                .authProvider(User.AuthProvider.GOOGLE)
                .role(User.Role.TRAVELER)
                .build()); // password stays null

        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"google@example.com\"}"));
        String code = codeFor(googleUser);

        mvc.perform(post(RESET).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"google@example.com\",\"code\":\"" + code + "\","
                                + "\"newPassword\":\"GoogleNewPw1\",\"confirmPassword\":\"GoogleNewPw1\"}"))
                .andExpect(status().isOk());

        User reloaded = users.findById(googleUser.getId()).orElseThrow();
        assertNotNull(reloaded.getPassword());
        assertTrue(passwordEncoder.matches("GoogleNewPw1", reloaded.getPassword()));

        // ── 9. authProvider/googleId are untouched — still a Google-linked account ──
        assertEquals(User.AuthProvider.GOOGLE, reloaded.getAuthProvider());
        assertEquals("google-sub-1", reloaded.getGoogleId());
    }

    // ── 10. refresh tokens are revoked after a successful reset ──
    @Test
    void resetPassword_revokesAllRefreshTokens() throws Exception {
        refreshTokens.save(RefreshToken.builder()
                .user(localUser)
                .token("token-1")
                .loginType(LoginHistory.LoginType.LOCAL)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build());
        refreshTokens.save(RefreshToken.builder()
                .user(localUser)
                .token("token-2")
                .loginType(LoginHistory.LoginType.LOCAL)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build());

        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));
        String code = codeFor(localUser);

        mvc.perform(post(RESET).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\","
                                + "\"newPassword\":\"BrandNewPass1\",\"confirmPassword\":\"BrandNewPass1\"}"))
                .andExpect(status().isOk());

        assertTrue(refreshTokens.findAllByUserAndRevokedFalse(localUser).isEmpty());
    }

    // edge: mismatched confirmation is rejected before touching the code
    @Test
    void resetPassword_mismatchedConfirmation_rejected() throws Exception {
        mvc.perform(post(FORGOT).contentType(APPLICATION_JSON).content("{\"email\":\"local@example.com\"}"));
        String code = codeFor(localUser);

        mvc.perform(post(RESET).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"local@example.com\",\"code\":\"" + code + "\","
                                + "\"newPassword\":\"BrandNewPass1\",\"confirmPassword\":\"Different99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("match")));

        // code is still unconsumed since we never got past confirmation matching
        assertTrue(codes.findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(
                localUser.getId(), CodeType.PASSWORD_RESET).isPresent());
    }
}
