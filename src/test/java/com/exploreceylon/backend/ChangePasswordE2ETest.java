package com.exploreceylon.backend;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for /api/v1/auth/change-password, covering both the
 * normal (email/password) change flow and the Google-only "create password" flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangePasswordE2ETest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String CHANGE_PASSWORD = "/api/v1/auth/change-password";

    private User localUser;
    private User googleUser;

    @BeforeEach
    void setUp() {
        users.deleteAll();

        localUser = users.save(User.builder()
                .name("Local Traveler")
                .email("local@example.com")
                .password(passwordEncoder.encode("OldPass123"))
                .role(User.Role.TRAVELER)
                .authProvider(User.AuthProvider.LOCAL)
                .build());

        googleUser = users.save(User.builder()
                .name("Google Traveler")
                .email("google@example.com")
                .role(User.Role.TRAVELER)
                .authProvider(User.AuthProvider.GOOGLE)
                .build()); // no password yet
    }

    private String body(String current, String next, String confirm) {
        StringBuilder sb = new StringBuilder("{");
        if (current != null) sb.append("\"currentPassword\":\"").append(current).append("\",");
        sb.append("\"newPassword\":\"").append(next).append("\"");
        if (confirm != null) sb.append(",\"confirmPassword\":\"").append(confirm).append("\"");
        sb.append("}");
        return sb.toString();
    }

    // ── 1. normal user changes password successfully ────────
    @Test
    void localUser_changesPassword_success() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(localUser))
                        .contentType(APPLICATION_JSON)
                        .content(body("OldPass123", "NewPass456", "NewPass456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("updated")));

        User reloaded = users.findById(localUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPass456", reloaded.getPassword()));
        assertEquals(User.AuthProvider.LOCAL, reloaded.getAuthProvider());
    }

    // ── 2. normal user enters wrong current password ────────
    @Test
    void localUser_wrongCurrentPassword_rejected() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(localUser))
                        .contentType(APPLICATION_JSON)
                        .content(body("WrongPass", "NewPass456", "NewPass456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("incorrect")));

        User reloaded = users.findById(localUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("OldPass123", reloaded.getPassword()));
    }

    // ── 3. Google user without password creates password successfully ──
    @Test
    void googleUser_createsPassword_success() throws Exception {
        assertNull(googleUser.getPassword());

        mvc.perform(post(CHANGE_PASSWORD).with(user(googleUser))
                        .contentType(APPLICATION_JSON)
                        .content(body(null, "NewPass456", "NewPass456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("created")));

        User reloaded = users.findById(googleUser.getId()).orElseThrow();
        assertNotNull(reloaded.getPassword());
        assertTrue(passwordEncoder.matches("NewPass456", reloaded.getPassword()));
        assertEquals(User.AuthProvider.GOOGLE, reloaded.getAuthProvider()); // provider unchanged
    }

    // ── 4. Google user, after creating a password, changes it normally ──
    @Test
    void googleUser_afterCreatingPassword_canChangeNormally() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(googleUser))
                        .contentType(APPLICATION_JSON)
                        .content(body(null, "FirstPass1", "FirstPass1")))
                .andExpect(status().isOk());

        User afterCreate = users.findById(googleUser.getId()).orElseThrow();

        mvc.perform(post(CHANGE_PASSWORD).with(user(afterCreate))
                        .contentType(APPLICATION_JSON)
                        .content(body("FirstPass1", "SecondPass2", "SecondPass2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("updated")));

        User reloaded = users.findById(googleUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("SecondPass2", reloaded.getPassword()));
        assertEquals(User.AuthProvider.GOOGLE, reloaded.getAuthProvider());
    }

    // ── 5. password is stored encrypted, never plain text ───
    @Test
    void password_isStoredEncrypted() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(localUser))
                        .contentType(APPLICATION_JSON)
                        .content(body("OldPass123", "PlainTextPw1", "PlainTextPw1")))
                .andExpect(status().isOk());

        User reloaded = users.findById(localUser.getId()).orElseThrow();
        assertNotEquals("PlainTextPw1", reloaded.getPassword());
        assertTrue(reloaded.getPassword().startsWith("$2")); // BCrypt hash prefix
    }

    // ── edge: confirmation mismatch is rejected for both flows ──
    @Test
    void mismatchedConfirmation_rejected() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(localUser))
                        .contentType(APPLICATION_JSON)
                        .content(body("OldPass123", "NewPass456", "Different789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("match")));
    }

    // ── edge: Google user submitting a currentPassword is simply ignored ──
    @Test
    void googleUser_submittingCurrentPassword_isIgnored() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(googleUser))
                        .contentType(APPLICATION_JSON)
                        .content(body("someBogusValue", "NewPass456", "NewPass456")))
                .andExpect(status().isOk());
    }

    // ── edge: too-short password rejected before any password check ──
    @Test
    void tooShortPassword_rejected() throws Exception {
        mvc.perform(post(CHANGE_PASSWORD).with(user(localUser))
                        .contentType(APPLICATION_JSON)
                        .content(body("OldPass123", "short", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("8 characters")));
    }
}
