package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.notification.NotificationResponse;
import com.exploreceylon.backend.service.BookingSchedulerService;
import com.exploreceylon.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService        notificationService;
    private final BookingSchedulerService     bookingSchedulerService;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    // GET /api/v1/notifications/my
    @GetMapping("/my")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        return ResponseEntity.ok(notificationService.getMyNotifications());
    }

    // GET /api/v1/notifications/unread-count
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount()));
    }

    // PATCH /api/v1/notifications/{id}/read
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    // PATCH /api/v1/notifications/read-all
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    // POST /api/v1/notifications/dev/trigger-reminders
    // Manually runs the daily balance-reminder scan (dev-mode only) — lets you
    // verify the feature without waiting for the 09:00 cron.
    @PostMapping("/dev/trigger-reminders")
    public ResponseEntity<Void> triggerReminders() {
        if (!devMode) return ResponseEntity.notFound().build();
        bookingSchedulerService.sendBalanceReminders();
        return ResponseEntity.ok().build();
    }
}
