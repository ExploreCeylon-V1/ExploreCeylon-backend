package com.exploreceylon.backend;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.VerificationCode;
import com.exploreceylon.backend.model.VerificationCode.CodeType;
import com.exploreceylon.backend.model.VerificationRateLimit;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.repository.VerificationCodeRepository;
import com.exploreceylon.backend.repository.VerificationRateLimitRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for real email + phone verification and rate limiting.
 * Runs the full Spring context against in-memory H2 with app.dev-mode=true, so
 * send endpoints return the code in JSON (no real email/SMS is dispatched).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationE2ETest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired VerificationCodeRepository codes;
    @Autowired VerificationRateLimitRepository limits;

    private static final String EMAIL_SEND   = "/api/v1/users/me/email/send-verification";
    private static final String EMAIL_VERIFY = "/api/v1/users/me/email/verify";
    private static final String PHONE_SEND   = "/api/v1/users/me/phone/send-otp";
    private static final String PHONE_VERIFY = "/api/v1/users/me/phone/verify-otp";
    private static final String UPDATE_ME    = "/api/v1/users/me";

    private User user;

    @BeforeEach
    void setUp() {
        limits.deleteAll();
        codes.deleteAll();
        users.deleteAll();
        user = users.save(User.builder()
                .name("Test Traveler")
                .email("test@example.com")
                .password("{noop}x")
                .phone("94770000000")
                .role(User.Role.TRAVELER)
                .build());
    }

    // ── helpers ─────────────────────────────────────────────
    private String send(String path) throws Exception {
        String json = mvc.perform(post(path).with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.devCode");   // present in dev-mode
    }

    private ResultActions verify(String path, String code) throws Exception {
        return mvc.perform(post(path).with(user(user))
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    /** Nudge the cooldown anchor back so a follow-up send isn't blocked by the 60s gap. */
    private void clearCooldown(CodeType type) {
        VerificationRateLimit rl = limits.findByUserIdAndType(user.getId(), type).orElseThrow();
        rl.setLastSentAt(LocalDateTime.now().minusMinutes(2));
        limits.save(rl);
    }

    // ── 1. successful email verification ────────────────────
    @Test
    void emailVerification_success() throws Exception {
        String code = send(EMAIL_SEND);
        verify(EMAIL_VERIFY, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertTrue(users.findById(user.getId()).orElseThrow().getEmailVerified());
    }

    // ── 2. successful phone verification ────────────────────
    @Test
    void phoneVerification_success() throws Exception {
        String code = send(PHONE_SEND);
        verify(PHONE_VERIFY, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(true));
        assertTrue(users.findById(user.getId()).orElseThrow().getPhoneVerified());
    }

    // ── 3. invalid code ─────────────────────────────────────
    @Test
    void invalidCode_isRejected() throws Exception {
        send(EMAIL_SEND);
        verify(EMAIL_VERIFY, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Invalid")));
        assertFalse(users.findById(user.getId()).orElseThrow().getEmailVerified());
    }

    // ── 4. expired code ─────────────────────────────────────
    @Test
    void expiredCode_isRejected() throws Exception {
        String code = send(EMAIL_SEND);
        VerificationCode vc = codes
                .findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(user.getId(), CodeType.EMAIL)
                .orElseThrow();
        vc.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        codes.save(vc);

        verify(EMAIL_VERIFY, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Invalid")));
        assertFalse(users.findById(user.getId()).orElseThrow().getEmailVerified());
    }

    // ── 5. max attempts exceeded → lockout (429) ────────────
    @Test
    void maxAttempts_locksVerification() throws Exception {
        send(EMAIL_SEND);
        // 4 wrong tries stay 400
        for (int i = 0; i < 4; i++) {
            verify(EMAIL_VERIFY, "000000").andExpect(status().isBadRequest());
        }
        // 5th wrong try trips the limit → 429 lock
        verify(EMAIL_VERIFY, "000000")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(containsString("locked")));
        // subsequent attempts remain locked
        verify(EMAIL_VERIFY, "000000")
                .andExpect(status().isTooManyRequests());
    }

    // ── 6. cooldown enforcement (429) ───────────────────────
    @Test
    void cooldown_blocksRapidResend() throws Exception {
        send(EMAIL_SEND);
        mvc.perform(post(EMAIL_SEND).with(user(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(containsString("wait")));
    }

    // ── 7. hourly rate limit (429) ──────────────────────────
    @Test
    void hourlyLimit_locksAfterFiveSends() throws Exception {
        for (int i = 0; i < 5; i++) {
            send(EMAIL_SEND);
            clearCooldown(CodeType.EMAIL);   // bypass the 60s gap, keep the hourly count
        }
        // 6th send within the hour → 429
        mvc.perform(post(EMAIL_SEND).with(user(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(containsString("Hourly")));
    }

    // ── 8a. phone change resets verification ────────────────
    @Test
    void phoneChange_resetsVerification() throws Exception {
        String code = send(PHONE_SEND);
        verify(PHONE_VERIFY, code).andExpect(status().isOk());
        assertTrue(users.findById(user.getId()).orElseThrow().getPhoneVerified());

        mvc.perform(put(UPDATE_ME).with(user(user))
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"94771111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(false));
        assertFalse(users.findById(user.getId()).orElseThrow().getPhoneVerified());
    }

    // ── 8b. email change resets verification ────────────────
    @Test
    void emailChange_resetsVerification() throws Exception {
        String code = send(EMAIL_SEND);
        verify(EMAIL_VERIFY, code).andExpect(status().isOk());
        assertTrue(users.findById(user.getId()).orElseThrow().getEmailVerified());

        mvc.perform(put(UPDATE_ME).with(user(user))
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"changed@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
        assertFalse(users.findById(user.getId()).orElseThrow().getEmailVerified());
    }
}
