package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactMessageRepository
        extends JpaRepository<ContactMessage, Long> {

    // All messages newest first
    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    // Unread only
    List<ContactMessage> findByIsReadFalseOrderByCreatedAtDesc();

    // Count unread
    long countByIsReadFalse();
}