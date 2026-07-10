package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.ContactMessageRequest;
import com.exploreceylon.backend.dto.ContactMessageResponse;
import com.exploreceylon.backend.service.ContactMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
public class ContactMessageController {

    private final ContactMessageService service;

    // ── Public — submit message ──────────────────────────
    // POST /api/v1/contact
    @PostMapping
    public ResponseEntity<ContactMessageResponse> submit(
            @Valid @RequestBody ContactMessageRequest req) {
        return ResponseEntity.ok(service.submit(req));
    }

    // ── Admin — get all messages ─────────────────────────
    // GET /api/v1/contact/admin
    @GetMapping("/admin")
    public ResponseEntity<List<ContactMessageResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // ── Admin — get unread only ──────────────────────────
    // GET /api/v1/contact/admin/unread
    @GetMapping("/admin/unread")
    public ResponseEntity<List<ContactMessageResponse>> getUnread() {
        return ResponseEntity.ok(service.getUnread());
    }

    // ── Admin — unread count (for badge) ────────────────
    // GET /api/v1/contact/admin/count
    @GetMapping("/admin/count")
    public ResponseEntity<Map<String, Long>> countUnread() {
        return ResponseEntity.ok(
                Map.of("unread", service.countUnread()));
    }

    // ── Admin — mark as read ─────────────────────────────
    // PATCH /api/v1/contact/admin/{id}/read
    @PatchMapping("/admin/{id}/read")
    public ResponseEntity<ContactMessageResponse> markRead(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.markAsRead(id));
    }

    // ── Admin — reply to message ─────────────────────────
    // POST /api/v1/contact/admin/{id}/reply
    @PostMapping("/admin/{id}/reply")
    public ResponseEntity<ContactMessageResponse> reply(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String replyText = body.get("reply");
        if (replyText == null || replyText.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.reply(id, replyText));
    }

    // ── Admin — delete message ───────────────────────────
    // DELETE /api/v1/contact/admin/{id}
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}