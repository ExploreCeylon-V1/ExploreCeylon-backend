package com.exploreceylon.backend.config;

import com.exploreceylon.backend.model.ChatConversation;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.ChatConversationRepository;
import com.exploreceylon.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Without this, any authenticated traveler could subscribe to /topic/chat.{anyId}
 * and read a stranger's conversation — STOMP topic subscriptions aren't
 * destination-scoped by Spring Security the way HTTP endpoints are.
 */
@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_PREFIX = "/topic/chat.";
    private static final String ADMIN_INBOX = "/topic/chat.admin-inbox";

    private final ChatConversationRepository conversationRepo;
    private final UserRepository userRepo;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal principal = accessor.getUser();

            if (destination == null || principal == null) {
                throw new SecurityException("Unauthorized chat subscription");
            }

            User user = userRepo.findByEmail(principal.getName()).orElse(null);
            if (user == null) {
                throw new SecurityException("Unauthorized chat subscription");
            }

            if (ADMIN_INBOX.equals(destination)) {
                if (user.getRole() != User.Role.ADMIN) {
                    throw new SecurityException("Admin-only destination");
                }
            } else if (destination.startsWith(CONVERSATION_PREFIX)) {
                Long conversationId = parseConversationId(destination);
                ChatConversation conv = conversationId == null
                        ? null : conversationRepo.findById(conversationId).orElse(null);
                boolean allowed = conv != null && (
                        user.getRole() == User.Role.ADMIN
                                || conv.getTravelerId().equals(user.getId()));
                if (!allowed) {
                    throw new SecurityException("Not authorized to subscribe to this conversation");
                }
            }
        }
        return message;
    }

    private Long parseConversationId(String destination) {
        try {
            return Long.parseLong(destination.substring(CONVERSATION_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
