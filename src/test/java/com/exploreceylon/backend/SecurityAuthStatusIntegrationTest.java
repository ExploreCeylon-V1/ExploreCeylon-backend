package com.exploreceylon.backend;

import com.exploreceylon.backend.config.JwtService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthStatusIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String ADMIN_ENDPOINT = "/api/v1/admin/guides/bookings";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void unauthenticated_protectedEndpoint_returns401Unauthorized() throws Exception {
        mvc.perform(get(ADMIN_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_protectedEndpoint_returns401Unauthorized() throws Exception {
        // Expired token (issued in 1970)
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjowLCJleHAiOjF9.signature";

        mvc.perform(get(ADMIN_ENDPOINT).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void travelerRole_adminEndpoint_returns403Forbidden() throws Exception {
        User traveler = userRepository.save(User.builder()
                .name("Test Traveler")
                .email("traveler@example.com")
                .password(passwordEncoder.encode("Password123"))
                .role(User.Role.TRAVELER)
                .authProvider(User.AuthProvider.LOCAL)
                .build());

        String travelerToken = jwtService.generateToken(traveler);

        mvc.perform(get(ADMIN_ENDPOINT).header("Authorization", "Bearer " + travelerToken))
                .andExpect(status().isForbidden());
    }
}
