package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.chat.ChatConversationResponse;
import com.exploreceylon.backend.dto.chat.ChatMessageRequest;
import com.exploreceylon.backend.dto.chat.ChatMessageResponse;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ── Traveler: get (or lazily create) my conversation ────────
    @GetMapping("/my-conversation")
    public ResponseEntity<ChatConversationResponse> myConversation(@AuthenticationPrincipal User traveler) {
        return ResponseEntity.ok(chatService.getOrCreateConversation(traveler));
    }

    // ── Traveler: message history ────────────────────────────────
    @GetMapping("/my-conversation/messages")
    public ResponseEntity<List<ChatMessageResponse>> myMessages(@AuthenticationPrincipal User traveler) {
        ChatConversationResponse conv = chatService.getOrCreateConversation(traveler);
        return ResponseEntity.ok(chatService.getMessages(conv.getId()));
    }

    // ── Traveler: send a message ─────────────────────────────────
    @PostMapping("/my-conversation/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal User traveler,
            @Valid @RequestBody ChatMessageRequest req) {
        return ResponseEntity.ok(chatService.sendAsTraveler(traveler, req.getContent()));
    }

    // ── Traveler: mark admin replies as read ─────────────────────
    @PatchMapping("/my-conversation/read")
    public ResponseEntity<Void> markMyConversationRead(@AuthenticationPrincipal User traveler) {
        chatService.markReadByTraveler(traveler);
        return ResponseEntity.ok().build();
    }

    // ── Admin: all conversations (shared inbox) ──────────────────
    @GetMapping("/admin/conversations")
    public ResponseEntity<List<ChatConversationResponse>> allConversations() {
        return ResponseEntity.ok(chatService.getAllConversations());
    }

    // ── Admin: unread conversation count (sidebar badge) ─────────
    @GetMapping("/admin/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("unread", chatService.countUnreadForAdmin()));
    }

    // ── Admin: message history for a conversation ────────────────
    @GetMapping("/admin/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> conversationMessages(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.getMessages(id));
    }

    // ── Admin: reply in a conversation ────────────────────────────
    @PostMapping("/admin/conversations/{id}/messages")
    public ResponseEntity<ChatMessageResponse> replyInConversation(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody ChatMessageRequest req) {
        return ResponseEntity.ok(chatService.sendAsAdmin(id, admin, req.getContent()));
    }

    // ── Admin: mark a conversation as read ────────────────────────
    @PatchMapping("/admin/conversations/{id}/read")
    public ResponseEntity<Void> markConversationRead(@PathVariable Long id) {
        chatService.markReadByAdmin(id);
        return ResponseEntity.ok().build();
    }
}
