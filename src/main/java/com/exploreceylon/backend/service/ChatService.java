package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.chat.ChatConversationResponse;
import com.exploreceylon.backend.dto.chat.ChatMessageResponse;
import com.exploreceylon.backend.model.ChatConversation;
import com.exploreceylon.backend.model.ChatMessage;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.ChatConversationRepository;
import com.exploreceylon.backend.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final long SYSTEM_SENDER_ID = 0L;

    private static final String WELCOME_AUTO_REPLY =
            "👋 Welcome to Explore Ceylon!\n\n" +
            "Thank you for contacting us. We've received your message, and one of our team " +
            "members will respond as soon as possible. If your inquiry is about trip planning, " +
            "bookings, or destinations, we're here to help!";

    private final ChatConversationRepository conversationRepo;
    private final ChatMessageRepository messageRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Traveler: get-or-create their single conversation ──────
    @Transactional
    public ChatConversationResponse getOrCreateConversation(User traveler) {
        ChatConversation conv = conversationRepo.findByTravelerId(traveler.getId())
                .orElseGet(() -> conversationRepo.save(ChatConversation.builder()
                        .travelerId(traveler.getId())
                        .travelerName(traveler.getName())
                        .travelerEmail(traveler.getEmail())
                        .lastMessage(null)
                        .build()));
        return toConversationResponse(conv);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream().map(this::toMessageResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> getAllConversations() {
        return conversationRepo.findAllByOrderByLastMessageAtDesc()
                .stream().map(this::toConversationResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countUnreadForAdmin() {
        return conversationRepo.countByUnreadByAdminTrue();
    }

    // ── Traveler sends a message ────────────────────────────────
    @Transactional
    public ChatMessageResponse sendAsTraveler(User traveler, String content) {
        ChatConversation conv = conversationRepo.findByTravelerId(traveler.getId())
                .orElseGet(() -> conversationRepo.save(ChatConversation.builder()
                        .travelerId(traveler.getId())
                        .travelerName(traveler.getName())
                        .travelerEmail(traveler.getEmail())
                        .build()));

        ChatMessage saved = messageRepo.save(ChatMessage.builder()
                .conversationId(conv.getId())
                .senderId(traveler.getId())
                .senderRole(ChatMessage.SenderRole.TRAVELER)
                .content(content.trim())
                .isRead(false)
                .build());

        conv.setLastMessage(saved.getContent());
        conv.setLastMessageAt(saved.getCreatedAt());
        conv.setUnreadByAdmin(true);
        conversationRepo.save(conv);

        ChatMessageResponse response = toMessageResponse(saved);
        broadcast(conv, response);

        // First message ever in this conversation → send the automated welcome reply.
        if (messageRepo.countByConversationId(conv.getId()) == 1) {
            sendWelcomeAutoReply(conv);
        }

        return response;
    }

    // ── Automated first-contact reply (not a real admin, doesn't clear unreadByAdmin) ─
    // Deliberately does NOT touch conv.lastMessage/lastMessageAt: the admin inbox preview
    // should keep showing the traveler's actual question, not this canned notice.
    private void sendWelcomeAutoReply(ChatConversation conv) {
        ChatMessage autoReply = messageRepo.save(ChatMessage.builder()
                .conversationId(conv.getId())
                .senderId(SYSTEM_SENDER_ID)
                .senderRole(ChatMessage.SenderRole.SYSTEM)
                .content(WELCOME_AUTO_REPLY)
                .isRead(false)
                .build());

        broadcast(conv, toMessageResponse(autoReply));
    }

    // ── Admin replies in a conversation ─────────────────────────
    @Transactional
    public ChatMessageResponse sendAsAdmin(Long conversationId, User admin, String content) {
        ChatConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        ChatMessage saved = messageRepo.save(ChatMessage.builder()
                .conversationId(conv.getId())
                .senderId(admin.getId())
                .senderRole(ChatMessage.SenderRole.ADMIN)
                .content(content.trim())
                .isRead(false)
                .build());

        conv.setLastMessage(saved.getContent());
        conv.setLastMessageAt(saved.getCreatedAt());
        conv.setUnreadByTraveler(true);
        conv.setUnreadByAdmin(false);
        conversationRepo.save(conv);

        ChatMessageResponse response = toMessageResponse(saved);
        broadcast(conv, response);
        return response;
    }

    @Transactional
    public void markReadByAdmin(Long conversationId) {
        ChatConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        conv.setUnreadByAdmin(false);
        conversationRepo.save(conv);
    }

    @Transactional
    public void markReadByTraveler(User traveler) {
        ChatConversation conv = conversationRepo.findByTravelerId(traveler.getId())
                .orElseThrow(() -> new RuntimeException("Conversation not found for traveler: " + traveler.getId()));
        conv.setUnreadByTraveler(false);
        conversationRepo.save(conv);
    }

    // ── Push new message + inbox update over STOMP ──────────────
    private void broadcast(ChatConversation conv, ChatMessageResponse message) {
        messagingTemplate.convertAndSend("/topic/chat." + conv.getId(), message);
        messagingTemplate.convertAndSend("/topic/chat.admin-inbox", toConversationResponse(conv));
    }

    private ChatMessageResponse toMessageResponse(ChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .senderRole(m.getSenderRole())
                .content(m.getContent())
                .isRead(m.getIsRead())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private ChatConversationResponse toConversationResponse(ChatConversation c) {
        return ChatConversationResponse.builder()
                .id(c.getId())
                .travelerId(c.getTravelerId())
                .travelerName(c.getTravelerName())
                .travelerEmail(c.getTravelerEmail())
                .lastMessage(c.getLastMessage())
                .lastMessageAt(c.getLastMessageAt())
                .unreadByAdmin(c.getUnreadByAdmin())
                .unreadByTraveler(c.getUnreadByTraveler())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
