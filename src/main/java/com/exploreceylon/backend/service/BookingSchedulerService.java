package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.GuideBooking;
import com.exploreceylon.backend.model.VehicleBooking;
import com.exploreceylon.backend.repository.GuideBookingRepository;
import com.exploreceylon.backend.repository.VehicleBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled maintenance for guide & vehicle bookings.
 *
 *  1. Expiry cleanup — PENDING_PAYMENT bookings that never paid the 20% advance
 *     within {@value #PENDING_EXPIRY_MINUTES} minutes are flipped to CANCELLED so
 *     they stop blocking the dates for other travelers.
 *
 *  2. Balance reminder — CONFIRMED bookings whose service ends today get a
 *     reminder for the remaining 80% balance, surfaced via the in-app
 *     notification bell (NotificationService). No email/SMS provider is wired
 *     yet (app.dev-mode), so this is the only channel for now.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSchedulerService {

    private static final long PENDING_EXPIRY_MINUTES = 30;

    private final GuideBookingRepository   guideBookingRepo;
    private final VehicleBookingRepository vehicleBookingRepo;
    private final NotificationService      notificationService;

    // Every 5 minutes — expire unpaid PENDING_PAYMENT bookings
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void expirePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);

        List<GuideBooking> guideStale = guideBookingRepo
                .findByStatusAndCreatedAtBefore(GuideBooking.BookingStatus.PENDING_PAYMENT, cutoff);
        guideStale.forEach(b -> b.setStatus(GuideBooking.BookingStatus.CANCELLED));
        if (!guideStale.isEmpty()) {
            guideBookingRepo.saveAll(guideStale);
            log.info("Expired {} unpaid guide booking(s) → CANCELLED", guideStale.size());
        }

        List<VehicleBooking> vehicleStale = vehicleBookingRepo
                .findByStatusAndCreatedAtBefore(VehicleBooking.BookingStatus.PENDING_PAYMENT, cutoff);
        vehicleStale.forEach(b -> b.setStatus(VehicleBooking.BookingStatus.CANCELLED));
        if (!vehicleStale.isEmpty()) {
            vehicleBookingRepo.saveAll(vehicleStale);
            log.info("Expired {} unpaid vehicle booking(s) → CANCELLED", vehicleStale.size());
        }
    }

    // Daily at 09:00 — remind travelers to pay the 80% balance.
    // Also callable on demand (see NotificationController's dev trigger) since
    // waiting for the cron makes this hard to verify manually.
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendBalanceReminders() {
        LocalDate today = LocalDate.now();

        List<GuideBooking> guideDue = guideBookingRepo
                .findByStatusAndEndDate(GuideBooking.BookingStatus.CONFIRMED, today);
        guideDue.forEach(b -> {
            log.info("BALANCE REMINDER — guide booking {} for user {} ends today; 80% balance ${} due",
                    b.getId(), b.getUser().getEmail(), b.getBalanceAmount());
            notificationService.createBalanceReminder(
                    b.getUser(), "GUIDE", b.getId(),
                    "Balance payment due",
                    String.format("Your guide service with %s ended today — pay the remaining $%.2f to complete your booking.",
                            b.getGuide().getFullName(), b.getBalanceAmount()));
        });

        List<VehicleBooking> vehicleDue = vehicleBookingRepo
                .findByStatusAndDropoffDate(VehicleBooking.BookingStatus.CONFIRMED, today);
        vehicleDue.forEach(b -> {
            log.info("BALANCE REMINDER — vehicle booking {} for user {} ends today; 80% balance ${} due",
                    b.getId(), b.getUser().getEmail(), b.getBalanceAmount());
            notificationService.createBalanceReminder(
                    b.getUser(), "VEHICLE", b.getId(),
                    "Balance payment due",
                    String.format("Your %s rental ended today — pay the remaining $%.2f to complete your booking.",
                            b.getVehicle().getName(), b.getBalanceAmount()));
        });
    }
}
