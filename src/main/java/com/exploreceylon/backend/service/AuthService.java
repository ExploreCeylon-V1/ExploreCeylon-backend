package com.exploreceylon.backend.service;

import com.exploreceylon.backend.config.JwtService;
import com.exploreceylon.backend.dto.auth.AuthResponse;
import com.exploreceylon.backend.dto.auth.LoginRequest;
import com.exploreceylon.backend.dto.auth.RegisterRequest;
import com.exploreceylon.backend.dto.auth.GoogleLoginRequest;
import com.exploreceylon.backend.model.User;
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
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        User.Role role = User.Role.TRAVELER;
        if (request.getRole() != null) {
            try {
                role = User.Role.valueOf(request.getRole().toUpperCase());
            } catch (Exception e) {
                log.warn("Invalid role: {}, defaulting to TRAVELER", request.getRole());
            }
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .nationality(request.getNationality())
                .language(request.getLanguage() != null ? request.getLanguage() : "en")
                .phone(request.getPhone())
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", user.getEmail());

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt: {}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("Login successful: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    // ─── Aluth Google Login Logic eka ───
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        log.info("Google login attempt");

        RestTemplate restTemplate = new RestTemplate();
        String googleUserInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";

        // Google Access Token eka header ekata danawa
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(request.getToken());
        HttpEntity<String> entity = new HttpEntity<>("", headers);

        try {
            // Google API eken User data gannawa
            ResponseEntity<Map> response = restTemplate.exchange(googleUserInfoUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> userInfo = response.getBody();

            if (userInfo == null || !userInfo.containsKey("email")) {
                throw new RuntimeException("Failed to retrieve user info from Google");
            }

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String picture = (String) userInfo.get("picture");

            // User database eke innawada kiyala balanawa, nathnam auto register karanawa
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                log.info("Creating new user from Google Login: {}", email);
                User newUser = User.builder()
                        .name(name)
                        .email(email)
                        // Google account eken ena nisa random security password ekak danawa
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role(User.Role.TRAVELER)
                        .profilePhoto(picture)
                        .language("en") // Default language
                        .build();
                return userRepository.save(newUser);
            });

            // ExploreCeylon eke standard JWT tokens generate karanawa
            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            log.info("Google Login successful: {}", email);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .userId(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .build();

        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new RuntimeException("Invalid Google token. Sign in failed.");
        }
    }
}