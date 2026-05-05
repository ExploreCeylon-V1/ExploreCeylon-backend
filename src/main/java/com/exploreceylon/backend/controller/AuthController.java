package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.auth.AuthResponse;
import com.exploreceylon.backend.dto.auth.LoginRequest;
import com.exploreceylon.backend.dto.auth.RegisterRequest;
import com.exploreceylon.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/v1/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // POST /api/v1/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // GET /api/v1/auth/me (protected)
    @GetMapping("/me")
    public ResponseEntity<String> me() {
        return ResponseEntity.ok("You are authenticated!");
    }
}