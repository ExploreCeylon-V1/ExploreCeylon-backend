package com.exploreceylon.backend;

import com.exploreceylon.backend.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit test for JwtService — no Spring context. jwt.secret /
 * jwt.expiration / jwt.refresh-expiration are normally injected via
 * @Value, so they're set here with ReflectionTestUtils since we're
 * instantiating the service directly.
 */
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "TestSecretKeyForExploreCeylonUnitTestsOnly1234567890";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86_400_000L); // 1 day
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L); // 7 days
    }

    private UserDetails userDetails(String email) {
        return new User(email, "irrelevant-password",
                Collections.emptyList());
    }

    @Test
    void generateToken_producesNonEmptyToken() {
        String token = jwtService.generateToken(userDetails("traveler@example.com"));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractUsername_returnsOriginalUsername() {
        String token = jwtService.generateToken(userDetails("traveler@example.com"));
        assertEquals("traveler@example.com", jwtService.extractUsername(token));
    }

    @Test
    void isTokenValid_forFreshlyGeneratedToken_returnsTrue() {
        UserDetails user = userDetails("traveler@example.com");
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_whenUsernameMismatch_returnsFalse() {
        UserDetails user = userDetails("traveler@example.com");
        String token = jwtService.generateToken(user);
        UserDetails otherUser = userDetails("someone-else@example.com");
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void generateToken_withExpiredExpiration_isRejectedAsInvalid() {
        // Build a token that is already expired by using a negative expiration window.
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        UserDetails user = userDetails("traveler@example.com");
        String token = jwtService.generateToken(user);

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                () -> jwtService.isTokenValid(token, user));
    }

    @Test
    void generateRefreshToken_producesNonEmptyToken() {
        String token = jwtService.generateRefreshToken(userDetails("traveler@example.com"));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }
}
