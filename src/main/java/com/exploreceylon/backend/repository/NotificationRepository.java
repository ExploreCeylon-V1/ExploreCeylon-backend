package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    // Guards the daily reminder job against creating duplicate notifications
    // if it runs more than once for the same booking.
    boolean existsByBookingTypeAndBookingIdAndType(
            String bookingType, Long bookingId, Notification.NotificationType type);
}
