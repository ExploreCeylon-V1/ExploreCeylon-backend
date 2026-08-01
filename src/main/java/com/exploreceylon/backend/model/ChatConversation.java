package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long travelerId;

    @Column(nullable = false)
    private String travelerName;

    @Column(nullable = false)
    private String travelerEmail;

    @Column(columnDefinition = "TEXT")
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    // True when there's an unread traveler message waiting on the admin side
    @Builder.Default
    private Boolean unreadByAdmin = false;

    // True when there's an unread admin reply waiting on the traveler side
    @Builder.Default
    private Boolean unreadByTraveler = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastMessageAt = LocalDateTime.now();
    }
}
