package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "subscribe_emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribeEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "subscribed_at", nullable = false, updatable = false)
    private Instant subscribedAt;

    @Builder.Default
    @Column(name = "added_to_group", nullable = false)
    private Boolean addedToGroup = false;

    @Column(name = "added_at")
    private Instant addedAt;

    @PrePersist
    protected void onCreate() {
        if (subscribedAt == null) {
            subscribedAt = Instant.now();
        }
        if (addedToGroup == null) {
            addedToGroup = false;
        }
    }
}
