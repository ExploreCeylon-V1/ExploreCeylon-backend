package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.notification.NotificationResponse;
import com.exploreceylon.backend.exception.UnauthenticatedException;
import com.exploreceylon.backend.model.Notification;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.NotificationRepository;
import com.exploreceylon.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserRepository         userRepo;

    // ── Create (idempotent per booking+type) ───────────────
    @Transactional
    public void createBalanceReminder(User user, String bookingType, Long bookingId,
                                       String title, String message) {
        boolean exists = notificationRepo.existsByBookingTypeAndBookingIdAndType(
                bookingType, bookingId, Notification.NotificationType.BALANCE_REMINDER);
        if (exists) return;

        Notification n = Notification.builder()
                .user(user)
                .type(Notification.NotificationType.BALANCE_REMINDER)
                .title(title)
                .message(message)
                .bookingType(bookingType)
                .bookingId(bookingId)
                .read(false)
                .build();
        notificationRepo.save(n);
    }

    // ── Admin-triggered overdue reminder ───────────────────
    @Transactional
    public boolean sendAdminOverdueReminder(User user, String bookingType, Long bookingId,
                                            String title, String message) {
        // Prevent duplicate spam if a reminder was already dispatched in the past 12 hours
        var existing = notificationRepo.findTopByBookingTypeAndBookingIdAndTypeOrderByCreatedAtDesc(
                bookingType, bookingId, Notification.NotificationType.BALANCE_REMINDER);
        if (existing.isPresent() && existing.get().getCreatedAt() != null &&
                existing.get().getCreatedAt().isAfter(java.time.LocalDateTime.now().minusHours(12))) {
            return false;
        }

        Notification n = Notification.builder()
                .user(user)
                .type(Notification.NotificationType.BALANCE_REMINDER)
                .title(title)
                .message(message)
                .bookingType(bookingType)
                .bookingId(bookingId)
                .read(false)
                .build();
        notificationRepo.save(n);
        return true;
    }

    @Transactional(readOnly = true)
    public java.time.LocalDateTime getLastReminderSentAt(String bookingType, Long bookingId) {
        return notificationRepo.findTopByBookingTypeAndBookingIdAndTypeOrderByCreatedAtDesc(
                bookingType, bookingId, Notification.NotificationType.BALANCE_REMINDER)
                .map(Notification::getCreatedAt)
                .orElse(null);
    }

    // ── Get my notifications ───────────────────────────────
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User user = getCurrentUser();
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User user = getCurrentUser();
        return notificationRepo.countByUserIdAndReadFalse(user.getId());
    }

    // ── Mark read ───────────────────────────────────────────
    @Transactional
    public void markAsRead(Long id) {
        User user = getCurrentUser();
        Notification n = notificationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
        if (!n.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your notification");
        }
        n.setRead(true);
        notificationRepo.save(n);
    }

    @Transactional
    public void markAllAsRead() {
        User user = getCurrentUser();
        List<Notification> unread = notificationRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().filter(n -> !n.isRead()).collect(Collectors.toList());
        unread.forEach(n -> n.setRead(true));
        notificationRepo.saveAll(unread);
    }

    // ── Mapper ──────────────────────────────────────────────
    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .bookingType(n.getBookingType())
                .bookingId(n.getBookingId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email).orElseThrow(() -> new UnauthenticatedException("User not found"));
    }
}
