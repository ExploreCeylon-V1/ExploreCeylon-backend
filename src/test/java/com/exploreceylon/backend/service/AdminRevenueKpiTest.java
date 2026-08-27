package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.DashboardStatsResponse;
import com.exploreceylon.backend.dto.admin.GuideStatsResponse;
import com.exploreceylon.backend.dto.admin.VehicleStatsResponse;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AdminRevenueKpiTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private VehicleBookingRepository vehicleBookingRepository;

    @Autowired
    private GuideBookingRepository guideBookingRepository;

    @Autowired
    private VehiclePaymentRepository vehiclePaymentRepository;

    @Autowired
    private GuidePaymentRepository guidePaymentRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TourGuideRepository tourGuideRepository;

    @Autowired
    private UserRepository userRepository;

    private User traveler;
    private Vehicle vehicle;
    private TourGuide guide;

    @BeforeEach
    void setUp() {
        vehiclePaymentRepository.deleteAll();
        guidePaymentRepository.deleteAll();
        vehicleBookingRepository.deleteAll();
        guideBookingRepository.deleteAll();
        vehicleRepository.deleteAll();
        tourGuideRepository.deleteAll();
        userRepository.deleteAll();

        traveler = userRepository.save(User.builder()
                .name("Alice Traveler")
                .email("alice.kpi@example.com")
                .role(User.Role.TRAVELER)
                .password("encoded_pass")
                .active(true)
                .build());

        Vehicle v = new Vehicle();
        v.setName("Toyota KDH High Roof");
        v.setType(Vehicle.VehicleType.VAN);
        v.setDistrict("Colombo");
        v.setPricePerDay(100.0);
        v.setAvailable(true);
        v.setSeats(10);
        vehicle = vehicleRepository.save(v);

        guide = tourGuideRepository.save(TourGuide.builder()
                .fullName("Sunil Perera")
                .district("Kandy")
                .languages("English, Sinhala")
                .specialties("CULTURAL, WILDLIFE")
                .phone("+94771234567")
                .pricePerDay(50.0)
                .available(true)
                .verified(true)
                .build());
    }

    @Test
    @DisplayName("1. COMPLETED booking with totalCost = 100,000 -> revenue includes 100,000")
    void testCompletedBookingWithFullTotalCost() {
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().minusDays(5))
                .dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.COMPLETED)
                .totalCost(100000.0)
                .advanceAmount(20000.0)
                .balanceAmount(80000.0)
                .build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.COMPLETED)
                .totalCost(100000.0)
                .advanceAmount(20000.0)
                .balanceAmount(80000.0)
                .build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();

        assertEquals(100000.0, vStats.getTotalRevenue(), "Vehicle revenue should equal full 100,000 totalCost");
        assertEquals(100000.0, gStats.getTotalRevenue(), "Guide revenue should equal full 100,000 totalCost");
    }

    @Test
    @DisplayName("2. CONFIRMED booking with 20% paid -> revenue includes 0")
    void testConfirmedBookingWithAdvancePaidContributesZero() {
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().plusDays(2))
                .dropoffDate(LocalDate.now().plusDays(5))
                .status(VehicleBooking.BookingStatus.CONFIRMED)
                .totalCost(50000.0)
                .advanceAmount(10000.0)
                .balanceAmount(40000.0)
                .build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(5))
                .status(GuideBooking.BookingStatus.CONFIRMED)
                .totalCost(30000.0)
                .advanceAmount(6000.0)
                .balanceAmount(24000.0)
                .build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();

        assertEquals(0.0, vStats.getTotalRevenue(), "Confirmed vehicle bookings with 20% advance must contribute 0 to Total Revenue");
        assertEquals(0.0, gStats.getTotalRevenue(), "Confirmed guide bookings with 20% advance must contribute 0 to Total Revenue");
    }

    @Test
    @DisplayName("3. CANCELLED booking with 20% paid -> revenue includes 0")
    void testCancelledBookingWithAdvancePaidContributesZero() {
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().minusDays(10))
                .dropoffDate(LocalDate.now().minusDays(5))
                .status(VehicleBooking.BookingStatus.CANCELLED)
                .totalCost(40000.0)
                .advanceAmount(8000.0)
                .balanceAmount(32000.0)
                .build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().minusDays(5))
                .status(GuideBooking.BookingStatus.CANCELLED)
                .totalCost(25000.0)
                .advanceAmount(5000.0)
                .balanceAmount(20000.0)
                .build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();

        assertEquals(0.0, vStats.getTotalRevenue(), "Cancelled vehicle booking must contribute 0 revenue");
        assertEquals(0.0, gStats.getTotalRevenue(), "Cancelled guide booking must contribute 0 revenue");
    }

    @Test
    @DisplayName("4. PENDING_PAYMENT booking -> revenue includes 0")
    void testPendingPaymentBookingContributesZero() {
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().plusDays(5))
                .dropoffDate(LocalDate.now().plusDays(7))
                .status(VehicleBooking.BookingStatus.PENDING_PAYMENT)
                .totalCost(60000.0)
                .advanceAmount(12000.0)
                .balanceAmount(48000.0)
                .build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(7))
                .status(GuideBooking.BookingStatus.PENDING_PAYMENT)
                .totalCost(15000.0)
                .advanceAmount(3000.0)
                .balanceAmount(12000.0)
                .build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();

        assertEquals(0.0, vStats.getTotalRevenue());
        assertEquals(0.0, gStats.getTotalRevenue());
    }

    @Test
    @DisplayName("5. Multiple completed bookings -> sum their FULL totalCost values")
    void testMultipleCompletedBookingsSumFullTotalCost() {
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().minusDays(10))
                .dropoffDate(LocalDate.now().minusDays(7))
                .status(VehicleBooking.BookingStatus.COMPLETED)
                .totalCost(50000.0)
                .advanceAmount(10000.0)
                .balanceAmount(40000.0)
                .build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Kandy")
                .pickupDate(LocalDate.now().minusDays(6))
                .dropoffDate(LocalDate.now().minusDays(2))
                .status(VehicleBooking.BookingStatus.COMPLETED)
                .totalCost(75000.0)
                .advanceAmount(15000.0)
                .balanceAmount(60000.0)
                .build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().minusDays(7))
                .status(GuideBooking.BookingStatus.COMPLETED)
                .totalCost(20000.0)
                .advanceAmount(4000.0)
                .balanceAmount(16000.0)
                .build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().minusDays(6))
                .endDate(LocalDate.now().minusDays(2))
                .status(GuideBooking.BookingStatus.COMPLETED)
                .totalCost(35000.0)
                .advanceAmount(7000.0)
                .balanceAmount(28000.0)
                .build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();

        assertEquals(125000.0, vStats.getTotalRevenue(), "50000 + 75000 = 125000");
        assertEquals(55000.0, gStats.getTotalRevenue(), "20000 + 35000 = 55000");
    }

    @Test
    @DisplayName("6. Mixture of COMPLETED + CONFIRMED + CANCELLED + PENDING bookings -> only COMPLETED full totalCost values are included")
    void testMixtureOfBookingStatuses() {
        // Vehicle bookings
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("A")
                .pickupDate(LocalDate.now().minusDays(5)).dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.COMPLETED).totalCost(1000.0)
                .advanceAmount(200.0).balanceAmount(800.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("B")
                .pickupDate(LocalDate.now().minusDays(5)).dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.CONFIRMED).totalCost(2000.0).advanceAmount(400.0).balanceAmount(1600.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("C")
                .pickupDate(LocalDate.now().minusDays(5)).dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.CANCELLED).totalCost(3000.0).advanceAmount(600.0).balanceAmount(2400.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("D")
                .pickupDate(LocalDate.now().plusDays(1)).dropoffDate(LocalDate.now().plusDays(3))
                .status(VehicleBooking.BookingStatus.PENDING_PAYMENT).totalCost(4000.0).advanceAmount(800.0).balanceAmount(3200.0).build());

        // Guide bookings
        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.COMPLETED).totalCost(500.0)
                .advanceAmount(100.0).balanceAmount(400.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.CONFIRMED).totalCost(800.0).advanceAmount(160.0).balanceAmount(640.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.CANCELLED).totalCost(900.0).advanceAmount(180.0).balanceAmount(720.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3))
                .status(GuideBooking.BookingStatus.PENDING_PAYMENT).totalCost(1200.0).advanceAmount(240.0).balanceAmount(960.0).build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();

        assertEquals(1000.0, vStats.getTotalRevenue(), "Only COMPLETED booking totalCost (1000) should be included");
        assertEquals(500.0, gStats.getTotalRevenue(), "Only COMPLETED booking totalCost (500) should be included");
    }

    @Test
    @DisplayName("7. Multiple payment attempts for the same booking must not cause revenue double-counting")
    void testMultiplePaymentAttemptsDoNotCauseDoubleCounting() {
        VehicleBooking vb = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupLocation("Colombo")
                .pickupDate(LocalDate.now().minusDays(5))
                .dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.COMPLETED)
                .totalCost(80000.0)
                .advanceAmount(16000.0)
                .balanceAmount(64000.0)
                .build());

        // Create 2 advance payment records (1 failed, 1 successful)
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(vb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(16000.0)
                .commissionAmount(2400.0)
                .driverPayout(13600.0)
                .status(VehiclePayment.PaymentStatus.FAILED)
                .build());

        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(vb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.ADVANCE)
                .phasePercent(20)
                .amount(16000.0)
                .commissionAmount(2400.0)
                .driverPayout(13600.0)
                .status(VehiclePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(5))
                .build());

        // Create 2 final payment records (1 pending, 1 successful)
        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(vb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.FINAL)
                .phasePercent(80)
                .amount(64000.0)
                .commissionAmount(9600.0)
                .driverPayout(54400.0)
                .status(VehiclePayment.PaymentStatus.PENDING)
                .build());

        vehiclePaymentRepository.save(VehiclePayment.builder()
                .vehicleBooking(vb)
                .user(traveler)
                .paymentPhase(VehiclePayment.PaymentPhase.FINAL)
                .phasePercent(80)
                .amount(64000.0)
                .commissionAmount(9600.0)
                .driverPayout(54400.0)
                .status(VehiclePayment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusDays(1))
                .build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();

        // Must equal booking.totalCost (80,000.0), without any double counting from 4 payment rows
        assertEquals(80000.0, vStats.getTotalRevenue(), "Booking totalCost must be counted exactly once");
    }

    @Test
    @DisplayName("8. Dashboard Total Revenue equals vehicles page Total Revenue + tour guides page Total Revenue")
    void testDashboardTotalRevenueEqualsVehiclePlusGuideRevenue() {
        // Vehicle COMPLETED
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("Colombo")
                .pickupDate(LocalDate.now().minusDays(5)).dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.COMPLETED).totalCost(120000.0)
                .advanceAmount(24000.0).balanceAmount(96000.0).build());

        // Guide COMPLETED
        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.COMPLETED).totalCost(80000.0)
                .advanceAmount(16000.0).balanceAmount(64000.0).build());

        // Also add some CONFIRMED, PENDING, CANCELLED bookings that should NOT be in completed revenue
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("Galle")
                .pickupDate(LocalDate.now().plusDays(2)).dropoffDate(LocalDate.now().plusDays(5))
                .status(VehicleBooking.BookingStatus.CONFIRMED).totalCost(50000.0)
                .advanceAmount(10000.0).balanceAmount(40000.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().plusDays(2)).endDate(LocalDate.now().plusDays(5))
                .status(GuideBooking.BookingStatus.CONFIRMED).totalCost(30000.0)
                .advanceAmount(6000.0).balanceAmount(24000.0).build());

        VehicleStatsResponse vStats = adminService.getVehicleStats();
        GuideStatsResponse gStats = adminService.getGuideStats();
        DashboardStatsResponse dStats = adminService.getDashboardStats();

        assertEquals(120000.0, vStats.getTotalRevenue(), "Vehicles page revenue");
        assertEquals(80000.0, gStats.getTotalRevenue(), "Guides page revenue");

        assertEquals(vStats.getTotalRevenue(), dStats.getVehicleRevenue(), "Dashboard vehicleRevenue matches Vehicles page");
        assertEquals(gStats.getTotalRevenue(), dStats.getGuideRevenue(), "Dashboard guideRevenue matches Guides page");
        assertEquals(vStats.getTotalRevenue() + gStats.getTotalRevenue(), dStats.getTotalRevenue(),
                "Dashboard Total Revenue must equal vehicles page Total Revenue + tour guides page Total Revenue (200,000.0)");
    }

    @Test
    @DisplayName("9. Dashboard shows confirmed vehicle bookings, confirmed guide bookings, and confirmed total bookings")
    void testDashboardConfirmedBookingsCount() {
        // 2 CONFIRMED vehicle bookings, 1 COMPLETED, 1 PENDING_PAYMENT, 1 CANCELLED
        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("A")
                .pickupDate(LocalDate.now().plusDays(1)).dropoffDate(LocalDate.now().plusDays(3))
                .status(VehicleBooking.BookingStatus.CONFIRMED).totalCost(1000.0)
                .advanceAmount(200.0).balanceAmount(800.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("B")
                .pickupDate(LocalDate.now().plusDays(4)).dropoffDate(LocalDate.now().plusDays(6))
                .status(VehicleBooking.BookingStatus.CONFIRMED).totalCost(2000.0)
                .advanceAmount(400.0).balanceAmount(1600.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("C")
                .pickupDate(LocalDate.now().minusDays(5)).dropoffDate(LocalDate.now().minusDays(1))
                .status(VehicleBooking.BookingStatus.COMPLETED).totalCost(3000.0)
                .advanceAmount(600.0).balanceAmount(2400.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("D")
                .pickupDate(LocalDate.now().plusDays(7)).dropoffDate(LocalDate.now().plusDays(9))
                .status(VehicleBooking.BookingStatus.PENDING_PAYMENT).totalCost(4000.0)
                .advanceAmount(800.0).balanceAmount(3200.0).build());

        vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler).vehicle(vehicle).pickupLocation("E")
                .pickupDate(LocalDate.now().plusDays(10)).dropoffDate(LocalDate.now().plusDays(12))
                .status(VehicleBooking.BookingStatus.CANCELLED).totalCost(5000.0)
                .advanceAmount(1000.0).balanceAmount(4000.0).build());

        // 3 CONFIRMED guide bookings, 1 COMPLETED, 1 PENDING_PAYMENT
        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3))
                .status(GuideBooking.BookingStatus.CONFIRMED).totalCost(500.0)
                .advanceAmount(100.0).balanceAmount(400.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().plusDays(4)).endDate(LocalDate.now().plusDays(6))
                .status(GuideBooking.BookingStatus.CONFIRMED).totalCost(600.0)
                .advanceAmount(120.0).balanceAmount(480.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().plusDays(7)).endDate(LocalDate.now().plusDays(9))
                .status(GuideBooking.BookingStatus.CONFIRMED).totalCost(700.0)
                .advanceAmount(140.0).balanceAmount(560.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().minusDays(1))
                .status(GuideBooking.BookingStatus.COMPLETED).totalCost(800.0)
                .advanceAmount(160.0).balanceAmount(640.0).build());

        guideBookingRepository.save(GuideBooking.builder()
                .user(traveler).guide(guide)
                .startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now().plusDays(12))
                .status(GuideBooking.BookingStatus.PENDING_PAYMENT).totalCost(900.0)
                .advanceAmount(180.0).balanceAmount(720.0).build());

        DashboardStatsResponse dStats = adminService.getDashboardStats();

        assertEquals(2L, dStats.getVehicleBookings(), "Vehicle Bookings total on dashboard must equal confirmed vehicle bookings (2)");
        assertEquals(3L, dStats.getGuideBookings(), "Guide Bookings total on dashboard must equal confirmed guide bookings (3)");
        assertEquals(5L, dStats.getTotalBookings(), "Total Bookings on dashboard must equal confirmed total bookings (2 + 3 = 5)");
    }
}
