package com.exploreceylon.backend;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage for basic register behavior and password security (Task 7). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthE2ETest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    private static final String REGISTER = "/api/v1/auth/register";

    @BeforeEach
    void setUp() {
        users.deleteAll();
    }

    // ── 12. password is stored encrypted on register, never plain text ──
    @Test
    void register_success_storesEncryptedPassword() throws Exception {
        mvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"New Traveler\",\"email\":\"newbie@example.com\",\"password\":\"StrongPass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());

        User created = users.findByEmail("newbie@example.com").orElseThrow();
        assertNotEquals("StrongPass1", created.getPassword());
        assertTrue(created.getPassword().startsWith("$2"));
        assertEquals(User.AuthProvider.LOCAL, created.getAuthProvider());
    }

    // password shorter than the 8-char minimum is rejected (consistent with change-password)
    @Test
    void register_tooShortPassword_rejected() throws Exception {
        mvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Weak Pw\",\"email\":\"weak@example.com\",\"password\":\"short1\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(users.findByEmail("weak@example.com").isEmpty());
    }

    // duplicate email is rejected, no second row created
    @Test
    void register_duplicateEmail_rejected() throws Exception {
        mvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"First\",\"email\":\"dup@example.com\",\"password\":\"StrongPass1\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"email\":\"dup@example.com\",\"password\":\"StrongPass1\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(1, users.count());
    }
}
