package com.exploreceylon.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkIdsRequest {
    @NotEmpty
    private List<Long> ids;

    // The ACTING admin's own password — required to confirm bulk activate/deactivate.
    @NotBlank
    private String password;
}
