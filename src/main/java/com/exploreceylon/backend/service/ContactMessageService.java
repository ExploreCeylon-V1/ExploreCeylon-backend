package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.ContactMessageRequest;
import com.exploreceylon.backend.dto.ContactMessageResponse;
import com.exploreceylon.backend.model.ContactMessage;
import com.exploreceylon.backend.repository.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactMessageService {

    private final ContactMessageRepository repo;

    // ── Submit (public) ─────────────────────────────────
    @Transactional
    public ContactMessageResponse submit(ContactMessageRequest req) {
        ContactMessage msg = ContactMessage.builder()
                .name(req.getName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .subject(req.getSubject() != null
                        ? req.getSubject().trim() : null)
                .message(req.getMessage().trim())
                .isRead(false)
                .build();

        ContactMessage saved = repo.save(msg);
        log.info("Contact message received from: {}", saved.getEmail());
        return toResponse(saved);
    }

    // ── Get all (admin) ─────────────────────────────────
    @Transactional(readOnly = true)
    public List<ContactMessageResponse> getAll() {
        return repo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get unread (admin) ──────────────────────────────
    @Transactional(readOnly = true)
    public List<ContactMessageResponse> getUnread() {
        return repo.findByIsReadFalseOrderByCreatedAtDesc()
                .stream().map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Count unread (admin dashboard badge) ───────────
    @Transactional(readOnly = true)
    public long countUnread() {
        return repo.countByIsReadFalse();
    }

    // ── Mark as read (admin) ────────────────────────────
    @Transactional
    public ContactMessageResponse markAsRead(Long id) {
        ContactMessage msg = repo.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Message not found: " + id));
        msg.setIsRead(true);
        return toResponse(repo.save(msg));
    }

    // ── Reply (admin) ───────────────────────────────────
    @Transactional
    public ContactMessageResponse reply(Long id, String replyText) {
        ContactMessage msg = repo.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Message not found: " + id));
        msg.setAdminReply(replyText.trim());
        msg.setRepliedAt(LocalDateTime.now());
        msg.setIsRead(true);
        return toResponse(repo.save(msg));
    }

    // ── Delete (admin) ──────────────────────────────────
    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
        log.info("Contact message deleted: {}", id);
    }

    // ── Mapper ──────────────────────────────────────────
    private ContactMessageResponse toResponse(ContactMessage m) {
        return ContactMessageResponse.builder()
                .id(m.getId())
                .name(m.getName())
                .email(m.getEmail())
                .subject(m.getSubject())
                .message(m.getMessage())
                .isRead(m.getIsRead())
                .adminReply(m.getAdminReply())
                .repliedAt(m.getRepliedAt())
                .createdAt(m.getCreatedAt())
                .build();
    }
}