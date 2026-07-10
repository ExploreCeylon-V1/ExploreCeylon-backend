package com.exploreceylon.backend.dto.admin;

import com.exploreceylon.backend.model.User;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {
    @NotNull
    private User.Role role;
}
