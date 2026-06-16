package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // GET /api/v1/users/me
    @GetMapping("/me")
    public ResponseEntity<?> getMe(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "id",           user.getId(),
                "name",         user.getName(),
                "email",        user.getEmail(),
                "phone",        user.getPhone() != null ? user.getPhone() : "",
                "profilePhoto", user.getProfilePhoto() != null ? user.getProfilePhoto() : "",
                "role",         user.getRole().name()
        ));
    }

    // POST /api/v1/users/me/photo  — upload profile photo
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProfilePhoto(
            @AuthenticationPrincipal User currentUser,
            @RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            String dataUrl = "data:" + contentType + ";base64," + base64;
            user.setProfilePhoto(dataUrl);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("profilePhoto", dataUrl));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to store profile photo"));
        }
    }

    // DELETE /api/v1/users/me/photo  — remove profile photo
    @DeleteMapping("/me/photo")
    public ResponseEntity<?> deleteProfilePhoto(
            @AuthenticationPrincipal User currentUser) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setProfilePhoto(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profile photo removed"));
    }

    // PUT /api/v1/users/me  — update name + phone only
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> body) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (body.containsKey("name") && !body.get("name").isBlank()) {
            user.setName(body.get("name"));
        }
        if (body.containsKey("phone")) {
            user.setPhone(body.get("phone"));
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "id",    user.getId(),
                "name",  user.getName(),
                "email", user.getEmail(),
                "phone", user.getPhone() != null ? user.getPhone() : ""
        ));
    }
}