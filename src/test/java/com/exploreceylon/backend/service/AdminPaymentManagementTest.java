package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AdminPaymentManagementTest {

    @Autowired private AdminService adminService;
    @Autowired private AdminPaymentQueryRepository adminPaymentQueryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private VehicleBookingRepository vehicleBookingRepository;
    @Autowired private VehiclePaymentRepository vehiclePaymentRepository;
    @Autowired private TourGuideRepository guideRepository;
    @Autowired private GuideBookingRepository guideBookingRepository;
    @Autowired private GuidePaymentRepository guidePaymentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TripRepository tripRepository;

    private User traveler;
    private Vehicle vehicle;
    private TourGuide guide;
    private Trip trip;

    private VehicleBooking confirmedVb;
    private VehicleBooking completedVb;
    private VehicleBooking pendingVb;
    private VehicleBooking cancelledVb;
    private VehicleBooking overdueVb;

    private GuideBooking confirmedGb;
    private GuideBooking overdueGb;

    @BeforeEach
    void setUp() {
        traveler = userRepository.save(User.builder()
                .name("Alice Traveler")
                .email("alice.test." + System.currentTimeMillis() + "@example.com")
                .password("encoded_pass")
                .phone("+94771122334")
                .role(User.Role.TRAVELER)
                .active(true)
                .build());

        Vehicle v = new Vehicle();
        v.setName("Toyota Prius Alpha");
        v.setType(Vehicle.VehicleType.CAR);
        v.setBrand("Toyota");
        v.setModel("Prius");
        v.setPricePerDay(50.0);
        v.setDistrict("Colombo");
        v.setDriverName("Kamal Perera");
        v.setDriverPhone("+94779988776");
        vehicle = vehicleRepository.save(v);

        guide = guideRepository.save(TourGuide.builder()
                .fullName("Sunil Fernando")
                .languages("English,Sinhala")
                .specialties("CULTURAL,WILDLIFE")
                .district("Kandy")
                .pricePerDay(60.0)
                .phone("+94712345678")
                .build());

        trip = tripRepository.save(Trip.builder()
                .user(traveler)
                .title("Ceylon Heritage Safari")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .build());

        // 1. Confirmed Vehicle Booking (20% paid, future service)
        confirmedVb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .trip(trip)
                .pickupDate(LocalDate.now().plusDays(5))
                .dropoffDate(LocalDate.now().plusDays(10))
                .pickupLocation("Colombo")
                .status(VehicleBooking.BookingStatus.CONFIRMED)
                .totalCost(250.0)
                .advanceAmount(50.0)
                .balanceAmount(200.0)
                .build());
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(confirmedVb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(50.0)
                .commissionAmount(7.5)
                .driverPayout(42.5)
                .payhereOrderId("VBK-" + confirmedVb.getId() + "-ADV-01")
                .status(VehiclePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(2))
                .build());

        // 2. Completed Vehicle Booking (100% paid)
        completedVb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupDate(LocalDate.now().minusDays(15))
                .dropoffDate(LocalDate.now().minusDays(10))
                .pickupLocation("Kandy")
                .status(VehicleBooking.BookingStatus.COMPLETED)
                .totalCost(300.0)
                .advanceAmount(60.0)
                .balanceAmount(240.0)
                .build());
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(completedVb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(60.0)
                .commissionAmount(9.0)
                .driverPayout(51.0)
                .payhereOrderId("VBK-" + completedVb.getId() + "-ADV-02")
                .status(VehiclePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(15))
                .build());
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(completedVb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.FINAL)
                .phasePercent(80)
                .amount(240.0)
                .commissionAmount(36.0)
                .driverPayout(204.0)
                .payhereOrderId("VBK-" + completedVb.getId() + "-FIN-02")
                .status(VehiclePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(10))
                .build());

        // 3. Overdue Vehicle Booking (20% paid, service ended 3 days ago, 80% unpaid)
        overdueVb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupDate(LocalDate.now().minusDays(8))
                .dropoffDate(LocalDate.now().minusDays(3))
                .pickupLocation("Galle")
                .status(VehicleBooking.BookingStatus.CONFIRMED)
                .totalCost(400.0)
                .advanceAmount(80.0)
                .balanceAmount(320.0)
                .build());
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(overdueVb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(80.0)
                .commissionAmount(12.0)
                .driverPayout(68.0)
                .payhereOrderId("VBK-" + overdueVb.getId() + "-ADV-03")
                .status(VehiclePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(8))
                .build());

        // 4. Pending Booking (Unpaid - Should be EXCLUDED)
        pendingVb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupDate(LocalDate.now().plusDays(2))
                .dropoffDate(LocalDate.now().plusDays(4))
                .pickupLocation("Colombo")
                .status(VehicleBooking.BookingStatus.PENDING_PAYMENT)
                .totalCost(100.0)
                .advanceAmount(20.0)
                .balanceAmount(80.0)
                .build());

        // 5. Cancelled Booking (Cancelled - Should be EXCLUDED)
        cancelledVb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupDate(LocalDate.now().plusDays(5))
                .dropoffDate(LocalDate.now().plusDays(7))
                .pickupLocation("Colombo")
                .status(VehicleBooking.BookingStatus.CANCELLED)
                .totalCost(100.0)
                .advanceAmount(20.0)
                .balanceAmount(80.0)
                .build());

        // 6. Confirmed Guide Booking
        confirmedGb = guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().plusDays(3))
                .endDate(LocalDate.now().plusDays(6))
                .status(GuideBooking.BookingStatus.CONFIRMED)
                .totalCost(180.0)
                .advanceAmount(36.0)
                .balanceAmount(144.0)
                .build());
        guidePaymentRepository.save(GuidePayment.builder()
                .guideBooking(confirmedGb)
                .user(traveler)
                .paymentPhase(GuidePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(36.0)
                .commissionAmount(5.4)
                .guidePayout(30.6)
                .payhereOrderId("GBK-" + confirmedGb.getId() + "-ADV-01")
                .status(GuidePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(1))
                .build());

        // 7. Overdue Guide Booking (service ended yesterday)
        overdueGb = guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().minusDays(4))
                .endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.CONFIRMED)
                .totalCost(200.0)
                .advanceAmount(40.0)
                .balanceAmount(160.0)
                .build());
        guidePaymentRepository.save(GuidePayment.builder()
                .guideBooking(overdueGb)
                .user(traveler)
                .paymentPhase(GuidePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(40.0)
                .commissionAmount(6.0)
                .guidePayout(34.0)
                .payhereOrderId("GBK-" + overdueGb.getId() + "-ADV-02")
                .status(GuidePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(4))
                .build());
    }

    @Test
    @DisplayName("Should list ONLY confirmed & completed bookings, excluding cancelled and pending")
    void testGetAllPaymentsExcludesCancelledAndPending() {
        PageResponse<AdminPaymentResponse> page = adminService.getAllPayments(
                "ALL", "ALL", null, null, null, "createdAt", "desc", 0, 20);

        assertNotNull(page);
        assertTrue(page.getTotalElements() >= 5);

        for (AdminPaymentResponse r : page.getContent()) {
            assertNotEquals("CANCELLED", r.getBookingStatus());
            assertNotEquals("PENDING_PAYMENT", r.getBookingStatus());
            assertTrue("CONFIRMED".equals(r.getBookingStatus()) || "COMPLETED".equals(r.getBookingStatus()));
        }
    }

    @Test
    @DisplayName("Should correctly filter by completion status (PARTIAL_20, FULL_100, OVERDUE)")
    void testFilterByCompletionStatus() {
        // Filter PARTIAL_20
        PageResponse<AdminPaymentResponse> partialPage = adminService.getAllPayments(
                "ALL", "PARTIAL_20", null, null, null, "createdAt", "desc", 0, 20);
        for (AdminPaymentResponse r : partialPage.getContent()) {
            assertEquals("20%", r.getPaymentCompletion());
            assertEquals("CONFIRMED", r.getBookingStatus());
        }

        // Filter FULL_100
        PageResponse<AdminPaymentResponse> fullPage = adminService.getAllPayments(
                "ALL", "FULL_100", null, null, null, "createdAt", "desc", 0, 20);
        for (AdminPaymentResponse r : fullPage.getContent()) {
            assertEquals("100%", r.getPaymentCompletion());
            assertEquals("COMPLETED", r.getBookingStatus());
            assertEquals(0.0, r.getRemainingBalance());
        }

        // Filter OVERDUE
        PageResponse<AdminPaymentResponse> overduePage = adminService.getAllPayments(
                "ALL", "OVERDUE", null, null, null, "createdAt", "desc", 0, 20);
        for (AdminPaymentResponse r : overduePage.getContent()) {
            assertTrue(r.isOverdue());
            assertTrue(r.getPaymentDueDate().isBefore(LocalDate.now()));
            assertEquals("CONFIRMED", r.getBookingStatus());
        }
    }

    @Test
    @DisplayName("Should compute correct KPI summary across all confirmed/completed bookings")
    void testPaymentSummaryKPIs() {
        AdminPaymentSummaryResponse summary = adminService.getPaymentSummary();
        assertNotNull(summary);
        assertTrue(summary.getTotalRevenueCollected() > 0.0);
        assertTrue(summary.getPartial20Count() >= 4);
        assertTrue(summary.getFull100Count() >= 1);
        assertTrue(summary.getOverdueCount() >= 2);
    }

    @Test
    @DisplayName("Should return complete payment details for vehicle booking with related trip")
    void testGetVehiclePaymentDetail() {
        AdminPaymentDetailResponse detail = adminService.getPaymentDetail("VEHICLE", confirmedVb.getId());
        assertNotNull(detail);
        assertEquals("VEHICLE", detail.getBookingType());
        assertEquals("CONFIRMED", detail.getBookingStatus());
        assertEquals("Alice Traveler", detail.getCustomerName());
        assertEquals("Toyota Prius Alpha", detail.getProviderName());
        assertEquals("20%", detail.getPaymentCompletion());
        assertEquals(250.0, detail.getTotalCost());
        assertEquals(50.0, detail.getAdvanceAmount());
        assertEquals(200.0, detail.getBalanceAmount());
        assertEquals(50.0, detail.getTotalPaid());
        assertEquals(200.0, detail.getRemainingBalance());
        assertNotNull(detail.getInitialPayment());
        assertEquals("COMPLETED", detail.getInitialPayment().getStatus());
        assertNotNull(detail.getFinalPayment());
        assertEquals("PENDING", detail.getFinalPayment().getStatus());
        assertEquals(trip.getId(), detail.getTripId());
        assertEquals("Ceylon Heritage Safari", detail.getTripTitle());
    }

    @Test
    @DisplayName("Should return complete payment details for guide booking")
    void testGetGuidePaymentDetail() {
        AdminPaymentDetailResponse detail = adminService.getPaymentDetail("GUIDE", confirmedGb.getId());
        assertNotNull(detail);
        assertEquals("GUIDE", detail.getBookingType());
        assertEquals("CONFIRMED", detail.getBookingStatus());
        assertEquals("Sunil Fernando", detail.getProviderName());
        assertEquals(180.0, detail.getTotalCost());
        assertEquals(36.0, detail.getAdvanceAmount());
        assertEquals(144.0, detail.getBalanceAmount());
        assertNotNull(detail.getInitialPayment());
        assertEquals("COMPLETED", detail.getInitialPayment().getStatus());
    }

    @Test
    @DisplayName("Should send admin overdue notification and prevent spam within 12 hours")
    void testNotifyOverdueUserAndDuplicateProtection() {
        // First notification to overdue booking -> should succeed
        Map<String, Object> result1 = adminService.notifyOverdueUser("VEHICLE", overdueVb.getId());
        assertTrue((Boolean) result1.get("success"));
        assertFalse((Boolean) result1.get("alreadySent"));

        // Second notification within 12 hours -> duplicate protection triggers
        Map<String, Object> result2 = adminService.notifyOverdueUser("VEHICLE", overdueVb.getId());
        assertFalse((Boolean) result2.get("success"));
        assertTrue((Boolean) result2.get("alreadySent"));
    }

    @Test
    @DisplayName("Should send custom message payment reminder to confirmed booking and store exact text")
    void testNotifyUserWithCustomMessage() {
        String custom = "Dear Alice, your remaining balance of $200.00 is due before pickup. Please settle today.";
        Map<String, Object> result = adminService.notifyOverdueUser("VEHICLE", confirmedVb.getId(), custom);
        assertTrue((Boolean) result.get("success"));

        var notif = notificationRepository.findTopByBookingTypeAndBookingIdAndTypeOrderByCreatedAtDesc(
                "VEHICLE", confirmedVb.getId(), Notification.NotificationType.BALANCE_REMINDER);
        assertTrue(notif.isPresent());
        assertEquals(custom, notif.get().getMessage());
        assertEquals(traveler.getId(), notif.get().getUser().getId());
    }

    @Test
    @DisplayName("Should reject blank custom message")
    void testRejectBlankCustomMessage() {
        assertThrows(RuntimeException.class, () -> {
            adminService.notifyOverdueUser("VEHICLE", confirmedVb.getId(), "    ");
        });
    }

    @Test
    @DisplayName("Should reject custom message exceeding 500 characters")
    void testRejectLongCustomMessage() {
        String longMsg = "A".repeat(501);
        assertThrows(RuntimeException.class, () -> {
            adminService.notifyOverdueUser("VEHICLE", confirmedVb.getId(), longMsg);
        });
    }

    @Test
    @DisplayName("Should reject reminder for already completed (100% paid) booking")
    void testRejectNotifyForCompletedBooking() {
        assertThrows(RuntimeException.class, () -> {
            adminService.notifyOverdueUser("VEHICLE", completedVb.getId(), "Please pay");
        });
    }

    @Test
    @DisplayName("Should reject reminder for cancelled booking")
    void testRejectNotifyForCancelledBooking() {
        assertThrows(RuntimeException.class, () -> {
            adminService.notifyOverdueUser("VEHICLE", cancelledVb.getId(), "Please pay");
        });
    }

    @Test
    @DisplayName("Should send reminder for Guide booking with custom message")
    void testNotifyGuideBookingWithCustomMessage() {
        String custom = "Please settle the remaining 80% balance for your tour guide booking with Sunil.";
        Map<String, Object> result = adminService.notifyOverdueUser("GUIDE", confirmedGb.getId(), custom);
        assertTrue((Boolean) result.get("success"));

        var notif = notificationRepository.findTopByBookingTypeAndBookingIdAndTypeOrderByCreatedAtDesc(
                "GUIDE", confirmedGb.getId(), Notification.NotificationType.BALANCE_REMINDER);
        assertTrue(notif.isPresent());
        assertEquals(custom, notif.get().getMessage());
    }

    @Test
    @DisplayName("Edge Case: Failed payment attempts must NOT mark phase as completed or alter paid amounts")
    void testFailedPaymentAttemptIgnored() {
        // Create booking with a failed payment attempt
        VehicleBooking vb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().plusDays(1))
                .dropoffDate(LocalDate.now().plusDays(3))
                .status(VehicleBooking.BookingStatus.PENDING_PAYMENT)
                .totalCost(150.0)
                .advanceAmount(30.0)
                .balanceAmount(120.0)
                .build());

        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(vb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(30.0)
                .commissionAmount(4.5)
                .driverPayout(25.5)
                .payhereOrderId("VBK-" + vb.getId() + "-ADV-FAIL")
                .status(VehiclePayment.PaymentStatus.FAILED)
                .build());

        // Should NOT appear in admin payments query because status is PENDING_PAYMENT
        PageResponse<AdminPaymentResponse> page = adminService.getAllPayments(
                "VEHICLE", "ALL", String.valueOf(vb.getId()), null, null, "createdAt", "desc", 0, 10);
        assertEquals(0, page.getContent().size());
    }

    @Test
    @DisplayName("Edge Case: Multiple payment attempts (1 failed, 1 completed) properly selects completed record")
    void testMultiplePaymentAttempts() {
        // Add a failed attempt to confirmedVb
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(confirmedVb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(50.0)
                .commissionAmount(7.5)
                .driverPayout(42.5)
                .payhereOrderId("VBK-" + confirmedVb.getId() + "-ADV-FAIL")
                .status(VehiclePayment.PaymentStatus.FAILED)
                .build());

        AdminPaymentDetailResponse detail = adminService.getPaymentDetail("VEHICLE", confirmedVb.getId());
        assertNotNull(detail);
        assertEquals("COMPLETED", detail.getInitialPayment().getStatus());
        assertEquals("VBK-" + confirmedVb.getId() + "-ADV-01", detail.getInitialPayment().getPayhereOrderId());
    }

    @Test
    @DisplayName("Edge Case: Booking without trip handles null trip gracefully")
    void testBookingWithoutTripHandledGracefully() {
        AdminPaymentDetailResponse detail = adminService.getPaymentDetail("VEHICLE", completedVb.getId());
        assertNotNull(detail);
        assertNull(detail.getTripId());
        assertNull(detail.getTripTitle());
    }

    @Test
    @DisplayName("Edge Case: Invalid booking type throws error")
    void testInvalidBookingTypeRejection() {
        assertThrows(RuntimeException.class, () -> {
            adminService.getPaymentDetail("HOTEL", 999L);
        });
        assertThrows(RuntimeException.class, () -> {
            adminService.notifyOverdueUser("FLIGHT", 999L);
        });
    }
}
