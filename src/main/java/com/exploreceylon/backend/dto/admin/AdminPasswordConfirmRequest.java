package com.exploreceylon.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Body for admin actions that need re-confirmation but otherwise take no other input
// (activate/deactivate a single user) — carries the ACTING admin's own password.
@Data
public class AdminPasswordConfirmRequest {
    @NotBlank
    private String password;
}
