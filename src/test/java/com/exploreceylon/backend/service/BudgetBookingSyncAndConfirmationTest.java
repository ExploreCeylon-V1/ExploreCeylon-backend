package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.budget.BudgetResponse;
import com.exploreceylon.backend.dto.email.BookingConfirmationDetails;
import com.exploreceylon.backend.dto.trip.SyncableBookingResponse;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BudgetBookingSyncAndConfirmationTest {

    @Autowired private BudgetService budgetService;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetItemRepository budgetItemRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private VehicleBookingRepository vehicleBookingRepository;
    @Autowired private TourGuideRepository guideRepository;
    @Autowired private GuideBookingRepository guideBookingRepository;
    @Autowired private EmailSenderService emailSenderService;

    private User traveler;
    private Trip trip;
    private Vehicle vehicle;
    private TourGuide guide;
    private VehicleBooking vehicleBooking;
    private GuideBooking guideBooking;

    @BeforeEach
    void setUp() {
        traveler = userRepository.save(User.builder()
                .name("Bob Explorer")
                .email("bob.explorer." + System.currentTimeMillis() + "@example.com")
                .password("encoded_pass")
                .role(User.Role.TRAVELER)
                .active(true)
                .build());

        // Set security context as traveler
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(traveler.getEmail(), "encoded_pass", List.of()));

        trip = tripRepository.save(Trip.builder()
                .user(traveler)
                .title("Southern Coast Getaway")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(10))
                .build());

        Vehicle v = new Vehicle();
        v.setName("Honda Vezel Hybrid");
        v.setType(Vehicle.VehicleType.SUV);
        v.setBrand("Honda");
        v.setModel("Vezel");
        v.setPricePerDay(65.0);
        v.setDistrict("Galle");
        v.setDriverName("Nimal Siripala");
        v.setDriverPhone("+94771122334");
        v.setEmail("nimal.driver@example.com");
        v.setLicensePlate("WP-CAD-4567");
        vehicle = vehicleRepository.save(v);

        guide = guideRepository.save(TourGuide.builder()
                .fullName("Chaminda Vaas")
                .languages("English,French")
                .specialties("WILDLIFE,HISTORICAL")
                .district("Galle")
                .pricePerDay(80.0)
                .phone("+94719876543")
                .email("chaminda.guide@example.com")
                .build());

        vehicleBooking = vehicleBookingRepository.save(VehicleBooking.builder()
                .user(traveler)
                .vehicle(vehicle)
                .pickupDate(LocalDate.now().plusDays(5))
                .dropoffDate(LocalDate.now().plusDays(8))
                .totalCost(195.0)
                .advanceAmount(39.0)
                .balanceAmount(156.0)
                .status(VehicleBooking.BookingStatus.CONFIRMED)
                .pickupLocation("Bandaranaike International Airport")
                .dropoffLocation("Galle Fort")
                .notes("Need child safety seat")
                .build());

        guideBooking = guideBookingRepository.save(GuideBooking.builder()
                .user(traveler)
                .guide(guide)
                .startDate(LocalDate.now().plusDays(6))
                .endDate(LocalDate.now().plusDays(7))
                .totalCost(160.0)
                .advanceAmount(32.0)
                .balanceAmount(128.0)
                .status(GuideBooking.BookingStatus.CONFIRMED)
                .notes("French speaking tour")
                .build());
    }

    @Test
    @DisplayName("Should retrieve syncable vehicle and guide bookings with accurate synced status")
    void testGetSyncableBookings() {
        List<SyncableBookingResponse> bookings = budgetService.getSyncableBookings(trip.getId());
        assertNotNull(bookings);
        assertEquals(2, bookings.size());

        SyncableBookingResponse vbRes = bookings.stream()
                .filter(b -> "VEHICLE".equals(b.getBookingType()))
                .findFirst()
                .orElse(null);
        assertNotNull(vbRes);
        assertEquals(vehicleBooking.getId(), vbRes.getBookingId());
        assertEquals("VB-" + vehicleBooking.getId(), vbRes.getReferenceId());
        assertEquals("WP-CAD-4567", vbRes.getVehicleNumber());
        assertEquals("nimal.driver@example.com", vbRes.getProviderEmail());
        assertEquals(195.0, vbRes.getTotalCost());
        assertFalse(vbRes.isSynced());

        SyncableBookingResponse gbRes = bookings.stream()
                .filter(b -> "GUIDE".equals(b.getBookingType()))
                .findFirst()
                .orElse(null);
        assertNotNull(gbRes);
        assertEquals(guideBooking.getId(), gbRes.getBookingId());
        assertEquals("GB-" + guideBooking.getId(), gbRes.getReferenceId());
        assertEquals("chaminda.guide@example.com", gbRes.getProviderEmail());
        assertEquals(160.0, gbRes.getTotalCost());
        assertFalse(gbRes.isSynced());
    }

    @Test
    @DisplayName("Should sync vehicle booking into budget items idempotently")
    void testSyncVehicleBookingToTripBudget() {
        BudgetResponse res = budgetService.syncBookingToTripBudget(trip.getId(), "VEHICLE", vehicleBooking.getId());
        assertNotNull(res);
        assertNotNull(res.getItems());

        boolean hasItem = res.getItems().stream()
                .anyMatch(item -> ("VB-" + vehicleBooking.getId()).equals(item.getReferenceId())
                        && item.getCategory() == BudgetItem.ItemCategory.VEHICLE
                        && item.getAmount() == 195.0
                        && Boolean.TRUE.equals(item.getAutoAdded()));
        assertTrue(hasItem, "Vehicle booking item should be present in budget items");

        // Booking trip association should be linked
        VehicleBooking refreshedVb = vehicleBookingRepository.findById(vehicleBooking.getId()).get();
        assertNotNull(refreshedVb.getTrip());
        assertEquals(trip.getId(), refreshedVb.getTrip().getId());

        // Idempotency check: Sync again should not create duplicate items
        BudgetResponse res2 = budgetService.syncBookingToTripBudget(trip.getId(), "VEHICLE", vehicleBooking.getId());
        long itemCount = res2.getItems().stream()
                .filter(item -> ("VB-" + vehicleBooking.getId()).equals(item.getReferenceId()))
                .count();
        assertEquals(1, itemCount, "Duplicate sync must not create duplicate items");

        // getSyncableBookings should now report isSynced = true
        List<SyncableBookingResponse> bookings = budgetService.getSyncableBookings(trip.getId());
        SyncableBookingResponse vbRes = bookings.stream()
                .filter(b -> "VEHICLE".equals(b.getBookingType()))
                .findFirst()
                .get();
        assertTrue(vbRes.isSynced());
    }

    @Test
    @DisplayName("Should sync guide booking into budget items idempotently")
    void testSyncGuideBookingToTripBudget() {
        BudgetResponse res = budgetService.syncBookingToTripBudget(trip.getId(), "GUIDE", guideBooking.getId());
        assertNotNull(res);

        boolean hasItem = res.getItems().stream()
                .anyMatch(item -> ("GB-" + guideBooking.getId()).equals(item.getReferenceId())
                        && item.getCategory() == BudgetItem.ItemCategory.GUIDE
                        && item.getAmount() == 160.0
                        && Boolean.TRUE.equals(item.getAutoAdded()));
        assertTrue(hasItem, "Guide booking item should be present in budget items");

        // Booking trip association should be linked
        GuideBooking refreshedGb = guideBookingRepository.findById(guideBooking.getId()).get();
        assertNotNull(refreshedGb.getTrip());
        assertEquals(trip.getId(), refreshedGb.getTrip().getId());

        // Idempotency check
        BudgetResponse res2 = budgetService.syncBookingToTripBudget(trip.getId(), "GUIDE", guideBooking.getId());
        long itemCount = res2.getItems().stream()
                .filter(item -> ("GB-" + guideBooking.getId()).equals(item.getReferenceId()))
                .count();
        assertEquals(1, itemCount);

        // getSyncableBookings reports isSynced = true
        List<SyncableBookingResponse> bookings = budgetService.getSyncableBookings(trip.getId());
        SyncableBookingResponse gbRes = bookings.stream()
                .filter(b -> "GUIDE".equals(b.getBookingType()))
                .findFirst()
                .get();
        assertTrue(gbRes.isSynced());
    }

    @Test
    @DisplayName("Should generate comprehensive HTML and plain text email templates for confirmed booking")
    void testBookingConfirmationEmailTemplates() {
        BookingConfirmationDetails details = BookingConfirmationDetails.builder()
                .customerName("Bob Explorer")
                .customerEmail("bob@example.com")
                .bookingType("VEHICLE")
                .referenceId("VB-" + vehicleBooking.getId())
                .providerName("Honda Vezel Hybrid")
                .providerPhone("+94771122334")
                .providerEmail("nimal.driver@example.com")
                .vehicleNumber("WP-CAD-4567")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 4))
                .pickupTime("09:00 AM")
                .dropoffTime("06:00 PM")
                .pickupLocation("Bandaranaike International Airport")
                .dropoffLocation("Galle Fort")
                .totalCost(195.0)
                .advanceAmount(39.0)
                .balanceAmount(156.0)
                .notes("Need child safety seat")
                .build();

        String html = EmailTemplates.bookingConfirmedHtml(details);
        assertNotNull(html);
        assertTrue(html.contains("Booking Confirmed!"));
        assertTrue(html.contains("VB-" + vehicleBooking.getId()));
        assertTrue(html.contains("Honda Vezel Hybrid"));
        assertTrue(html.contains("WP-CAD-4567"));
        assertTrue(html.contains("nimal.driver@example.com"));
        assertTrue(html.contains("195.00"));
        assertTrue(html.contains("39.00"));
        assertTrue(html.contains("156.00"));

        String text = EmailTemplates.bookingConfirmedPlainText(details);
        assertNotNull(text);
        assertTrue(text.contains("CONFIRMED"));
        assertTrue(text.contains("WP-CAD-4567"));
        assertTrue(text.contains("Need child safety seat"));

        // Test safe async execution (does not throw exception)
        assertDoesNotThrow(() -> emailSenderService.sendBookingConfirmation(details));
    }
}
