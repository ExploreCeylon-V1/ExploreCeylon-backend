package com.exploreceylon.backend;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for Google OAuth account linking. RestTemplate is now an
 * injectable bean (see WebClientConfig), which is what makes this @MockBean possible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleOAuthE2ETest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean RestTemplate restTemplate;

    private static final String GOOGLE_LOGIN = "/api/v1/auth/google";

    @BeforeEach
    void setUp() {
        users.deleteAll();
    }

    @SuppressWarnings("unchecked")
    private void stubGoogle(String email, String name, String sub) {
        Map<String, Object> body = Map.of("email", email, "name", name, "picture", "http://pic", "sub", sub);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    // ── 1. new Google user creation ──────────────────────────
    @Test
    void newGoogleUser_createsAccount() throws Exception {
        stubGoogle("new.google@example.com", "New Googler", "google-sub-1");

        mvc.perform(post(GOOGLE_LOGIN).contentType(APPLICATION_JSON).content("{\"token\":\"fake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.google@example.com"));

        User created = users.findByEmail("new.google@example.com").orElseThrow();
        assertEquals(User.AuthProvider.GOOGLE, created.getAuthProvider());
        assertEquals("google-sub-1", created.getGoogleId());
        assertTrue(created.getEmailVerified());
        assertNull(created.getPassword());
        assertEquals(1, users.count());
    }

    // ── 2. existing LOCAL user gets linked, not duplicated ───
    @Test
    void existingLocalUser_getsLinkedNotDuplicated() throws Exception {
        User local = users.save(User.builder()
                .name("Local Person")
                .email("shared@example.com")
                .password(passwordEncoder.encode("LocalPass1"))
                .authProvider(User.AuthProvider.LOCAL)
                .role(User.Role.TRAVELER)
                .build());

        stubGoogle("shared@example.com", "Shared Google Name", "google-sub-2");

        mvc.perform(post(GOOGLE_LOGIN).contentType(APPLICATION_JSON).content("{\"token\":\"fake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(local.getId()));

        User reloaded = users.findById(local.getId()).orElseThrow();
        assertEquals(User.AuthProvider.LOCAL, reloaded.getAuthProvider());   // never flipped
        assertEquals("google-sub-2", reloaded.getGoogleId());               // now linked
        assertNotNull(reloaded.getPassword());                              // kept
        assertTrue(passwordEncoder.matches("LocalPass1", reloaded.getPassword()));
        assertEquals(1, users.count());
    }

    // ── 3. existing GOOGLE user logs in normally, no duplicate ──
    @Test
    void existingGoogleUser_logsInNormally() throws Exception {
        User google = users.save(User.builder()
                .name("Google Person")
                .email("googler@example.com")
                .googleId("google-sub-3")
                .authProvider(User.AuthProvider.GOOGLE)
                .role(User.Role.TRAVELER)
                .emailVerified(true)
                .build());

        stubGoogle("googler@example.com", "Google Person", "google-sub-3");

        mvc.perform(post(GOOGLE_LOGIN).contentType(APPLICATION_JSON).content("{\"token\":\"fake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(google.getId()));

        assertEquals(1, users.count());
    }

    // ── 4. duplicate-email prevention across repeated Google logins ──
    @Test
    void repeatedGoogleLogins_neverDuplicateEmail() throws Exception {
        stubGoogle("repeat@example.com", "Repeat User", "google-sub-4");

        for (int i = 0; i < 3; i++) {
            mvc.perform(post(GOOGLE_LOGIN).contentType(APPLICATION_JSON).content("{\"token\":\"fake\"}"))
                    .andExpect(status().isOk());
        }

        assertEquals(1, users.count());
    }

    // ── edge: a deactivated Google account cannot log back in ──
    @Test
    void deactivatedGoogleUser_cannotLogin() throws Exception {
        users.save(User.builder()
                .name("Blocked Googler")
                .email("blocked@example.com")
                .googleId("google-sub-5")
                .authProvider(User.AuthProvider.GOOGLE)
                .role(User.Role.TRAVELER)
                .active(false)
                .build());

        stubGoogle("blocked@example.com", "Blocked Googler", "google-sub-5");

        mvc.perform(post(GOOGLE_LOGIN).contentType(APPLICATION_JSON).content("{\"token\":\"fake\"}"))
                .andExpect(status().isBadRequest());
    }
}
