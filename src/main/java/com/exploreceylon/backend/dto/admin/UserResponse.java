package com.exploreceylon.backend.dto.admin;

import com.exploreceylon.backend.model.User;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private User.Role role;
    private String nationality;
    private String language;
    private String phone;
    private LocalDateTime createdAt;
    private boolean active;
}