package com.exploreceylon.backend.dto.regeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a trip day impacted by a regeneration event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffectedDay {
    private int dayNumber;
    private String reasonForRegeneration;
    private boolean isLocked;
}
