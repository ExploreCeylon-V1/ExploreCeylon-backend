package com.exploreceylon.backend.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribeEmailResponseDTO {

    private Long id;
    private String email;
    private Instant subscribedAt;
    private boolean addedToGroup;
    private Instant addedAt;
}
