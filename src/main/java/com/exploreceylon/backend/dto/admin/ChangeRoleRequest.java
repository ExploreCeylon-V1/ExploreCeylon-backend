package com.exploreceylon.backend.dto.admin;

import com.exploreceylon.backend.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {
    @NotNull
    private User.Role role;

    // The ACTING admin's own password — re-confirms it's really them before
    // granting/revoking admin access, not the target user's password.
    @NotBlank
    private String password;
}
