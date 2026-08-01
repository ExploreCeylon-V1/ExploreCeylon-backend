package com.exploreceylon.backend.dto.maintenance;

import lombok.Data;

@Data
public class MaintenanceStatusResponse {
    private Boolean active;
    private String title;
    private String description;
}
