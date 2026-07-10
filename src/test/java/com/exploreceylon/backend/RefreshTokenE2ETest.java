package com.exploreceylon.backend;

import com.exploreceylon.backend.model.LoginHistory;
import com.exploreceylon.backend.model.RefreshToken;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.LoginHistoryRepository;
import com.exploreceylon.backend.repository.RefreshTokenRepository;
import com.exploreceylon.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for the refresh-token system, login history, session
 * listing/logout-all, and account deactivation blocking login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenE2ETest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginHistoryRepository loginHistoryRepo;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String LOGIN      = "/api/v1/auth/login";
    private static final String REFRESH    = "/api/v1/auth/refresh-token";
    private static final String LOGOUT     = "/api/v1/auth/logout";
    private static final String SESSIONS   = "/api/v1/users/sessions";
    private static final String LOGOUT_ALL = "/api/v1/users/sessions/logout-all";

    private User activeUser;

    @BeforeEach
    void setUp() {
        refreshTokens.deleteAll();
        loginHistoryRepo.deleteAll();
        users.deleteAll();
        activeUser = users.save(User.builder()
                .name("Active Traveler")
                .email("active@example.com")
                .password(passwordEncoder.encode("CorrectPass1"))
                .role(User.Role.TRAVELER)
                .authProvider(User.AuthProvider.LOCAL)
                .build());
    }

    private String loginBody() {
        return "{\"email\":\"active@example.com\",\"password\":\"CorrectPass1\"}";
    }

    // ── 5. login generates a persisted, usable refresh token ──
    @Test
    void login_generatesRefreshToken() throws Exception {
        String json = mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = JsonPath.read(json, "$.refreshToken");
        assertNotNull(refreshToken);
        assertTrue(refreshTokens.findByToken(refreshToken).isPresent());
    }

    // ── 6. refresh-token exchanges a valid refresh token for a new access token ──
    @Test
    void refreshToken_issuesNewAccessToken() throws Exception {
        String json = mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(json, "$.refreshToken");

        mvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").value(refreshToken));
    }

    // ── 7. expired refresh token is rejected ─────────────────
    @Test
    void expiredRefreshToken_isRejected() throws Exception {
        String expiredToken = UUID.randomUUID().toString();
        refreshTokens.save(RefreshToken.builder()
                .user(activeUser)
                .token(expiredToken)
                .loginType(LoginHistory.LoginType.LOCAL)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .revoked(false)
                .build());

        mvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + expiredToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("expired")));
    }

    // ── 8. logout revokes the refresh token ──────────────────
    @Test
    void logout_revokesRefreshToken() throws Exception {
        String json = mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(json, "$.refreshToken");

        mvc.perform(post(LOGOUT).contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("revoked")));
    }

    // ── 9. a successful login is recorded in login history ──
    @Test
    void login_recordsLoginHistory() throws Exception {
        mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk());

        List<LoginHistory> history = loginHistoryRepo.findByUserOrderByCreatedAtDesc(activeUser);
        assertEquals(1, history.size());
        assertEquals(LoginHistory.LoginType.LOCAL, history.get(0).getLoginType());
    }

    // ── 10. logout-all revokes every active session for the user ──
    @Test
    void logoutAll_revokesEverySession() throws Exception {
        String json1 = mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andReturn().getResponse().getContentAsString();
        String json2 = mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andReturn().getResponse().getContentAsString();
        String refresh1 = JsonPath.read(json1, "$.refreshToken");
        String refresh2 = JsonPath.read(json2, "$.refreshToken");

        User reloaded = users.findById(activeUser.getId()).orElseThrow();
        mvc.perform(delete(LOGOUT_ALL).with(user(reloaded)))
                .andExpect(status().isOk());

        mvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── sessions endpoint reflects active logins ─────────────
    @Test
    void sessions_listsActiveLogins() throws Exception {
        mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk());

        User reloaded = users.findById(activeUser.getId()).orElseThrow();
        mvc.perform(get(SESSIONS).with(user(reloaded)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loginType").value("LOCAL"));
    }

    // ── 11. a deactivated user cannot log in, and no session is left active ──
    @Test
    void deactivatedUser_cannotLogin() throws Exception {
        activeUser.setActive(false);
        users.save(activeUser);

        mvc.perform(post(LOGIN).contentType(APPLICATION_JSON).content(loginBody()))
                .andExpect(status().is4xxClientError());

        assertTrue(refreshTokens.findAllByUserAndRevokedFalse(activeUser).isEmpty());
    }
}
