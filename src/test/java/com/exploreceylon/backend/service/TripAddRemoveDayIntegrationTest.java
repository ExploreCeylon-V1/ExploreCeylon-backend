package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.trip.AddDayRequest;
import com.exploreceylon.backend.dto.trip.TripResponse;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripAddRemoveDayIntegrationTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripDayRepository tripDayRepository;

    @Autowired
    private TripDayItemRepository tripDayItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private VehicleBookingRepository vehicleBookingRepository;

    @Autowired
    private GuideBookingRepository guideBookingRepository;

    @Autowired
    private HotelBookingRepository hotelBookingRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TourGuideRepository tourGuideRepository;

    private User testUser;
    private Vehicle testVehicle;
    private TourGuide testGuide;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Day Planner Tester")
                .email("addremove_day_test_" + UUID.randomUUID() + "@example.com")
                .password("password123")
                .role(User.Role.TRAVELER)
                .build());

        Vehicle v = new Vehicle();
        v.setName("Test Prius");
        v.setType(Vehicle.VehicleType.CAR);
        v.setDistrict("Colombo");
        v.setPricePerDay(50.0);
        testVehicle = vehicleRepository.save(v);

        testGuide = tourGuideRepository.save(TourGuide.builder()
                .fullName("Test Guide Perera")
                .languages("English,Sinhala")
                .specialties("CULTURE_HERITAGE,WILDLIFE_NATURE")
                .district("Kandy")
                .pricePerDay(40.0)
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser.getEmail(), null, Collections.emptyList())
        );
    }

    private Trip createTestTrip(LocalDate start, LocalDate end, int dayCount) {
        Trip trip = Trip.builder()
                .user(testUser)
                .title("Original Coastal Journey")
                .fromLocation("Colombo")
                .toLocation("Galle")
                .startDate(start)
                .endDate(end)
                .travelStyle(Trip.TravelStyle.ADVENTURE)
                .budgetRange(Trip.BudgetRange.MID_RANGE)
                .groupSize(2)
                .status(Trip.TripStatus.DRAFT)
                .budgetAmountLkr(50000.0)
                .days(new ArrayList<>())
                .build();
        trip = tripRepository.save(trip);

        for (int i = 1; i <= dayCount; i++) {
            LocalDate dayDate = start.plusDays(i - 1);
            TripDay day = TripDay.builder()
                    .trip(trip)
                    .dayNumber(i)
                    .date(dayDate)
                    .region("Region " + i)
                    .theme("Theme for Day " + i)
                    .tips("Tips for Day " + i)
                    .estimatedDayCost(75.0)
                    .items(new ArrayList<>())
                    .build();
            day = tripDayRepository.save(day);

            TripDayItem item1 = TripDayItem.builder()
                    .tripDay(day)
                    .type(TripDayItem.ItemType.ACTIVITY)
                    .title("Stop 1 on Day " + i)
                    .cost(25.0)
                    .currency("USD")
                    .orderIndex(1)
                    .notes("[morning] Explore site 1")
                    .build();
            tripDayItemRepository.save(item1);
            day.getItems().add(item1);

            trip.getDays().add(day);
        }
        return tripRepository.save(trip);
    }

    @Test
    @DisplayName("Scenario 1: Add Day to Trip WITHOUT Budget row - date rollover, stops, immutability, lazy budget preservation")
    void testAddDayWithoutBudgetRow() {
        // Ends on 2026-01-31
        Trip trip = createTestTrip(LocalDate.of(2026, 1, 30), LocalDate.of(2026, 1, 31), 2);
        Long tripId = trip.getId();

        // Capture Day 1 & Day 2 snapshots
        TripDay day1Before = trip.getDays().get(0);
        TripDay day2Before = trip.getDays().get(1);
        String day1ThemeBefore = day1Before.getTheme();
        String day2ThemeBefore = day2Before.getTheme();
        Double day1CostBefore = day1Before.getEstimatedDayCost();
        Double day2CostBefore = day2Before.getEstimatedDayCost();
        int day1ItemCountBefore = day1Before.getItems().size();

        AddDayRequest req = AddDayRequest.builder()
                .targetArea("Ella")
                .travelStyles(List.of("ADVENTURE", "SCENIC_VIEWS"))
                .build();

        TripResponse res = tripService.addDayToTrip(tripId, req);

        // 1. Verify trip endDate updated to 2026-02-01 (month rollover)
        Trip updatedTrip = tripRepository.findById(tripId).orElseThrow();
        assertThat(updatedTrip.getEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(updatedTrip.getTitle()).isEqualTo("Original Coastal Journey"); // Title unchanged

        // 2. Verify 3 days now exist
        assertThat(updatedTrip.getDays()).hasSize(3);
        TripDay newDay = updatedTrip.getDays().stream()
                .filter(d -> d.getDayNumber() == 3)
                .findFirst().orElseThrow();

        assertThat(newDay.getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(newDay.getEstimatedDayCost()).isNotNull();
        assertThat(newDay.getRegion()).isNotBlank();
        assertThat(newDay.getTheme()).isNotBlank();

        // 3. Verify existing days are 100% immutable
        TripDay day1After = updatedTrip.getDays().stream().filter(d -> d.getDayNumber() == 1).findFirst().orElseThrow();
        TripDay day2After = updatedTrip.getDays().stream().filter(d -> d.getDayNumber() == 2).findFirst().orElseThrow();

        assertThat(day1After.getTheme()).isEqualTo(day1ThemeBefore);
        assertThat(day2After.getTheme()).isEqualTo(day2ThemeBefore);
        assertThat(day1After.getEstimatedDayCost()).isEqualTo(day1CostBefore);
        assertThat(day2After.getEstimatedDayCost()).isEqualTo(day2CostBefore);
        assertThat(day1After.getItems()).hasSize(day1ItemCountBefore);

        // 4. Verify NO budget row was created
        assertThat(budgetRepository.findByTripId(tripId)).isEmpty();

        // 5. Verify Trip.budgetAmountLkr increased
        assertThat(updatedTrip.getBudgetAmountLkr()).isGreaterThan(50000.0);
    }

    @Test
    @DisplayName("Scenario 2: Add Day to Trip WITH Budget row (Custom Allocations)")
    void testAddDayWithCustomBudgetRow() {
        Trip trip = createTestTrip(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), 2);
        Long tripId = trip.getId();

        // Create customized budget
        Map<BudgetItem.ItemCategory, Double> customAllocations = new HashMap<>();
        customAllocations.put(BudgetItem.ItemCategory.HOTEL, 400.0);
        customAllocations.put(BudgetItem.ItemCategory.VEHICLE, 200.0);
        customAllocations.put(BudgetItem.ItemCategory.MISC, 100.0);

        Budget budget = Budget.builder()
                .trip(trip)
                .user(testUser)
                .totalBudget(700.0)
                .currency("USD")
                .categoryBudgets(customAllocations)
                .build();
        budgetRepository.save(budget);

        AddDayRequest req = AddDayRequest.builder()
                .targetArea("Kandy")
                .travelStyles(List.of("CULTURE_HERITAGE", "RELIGIOUS"))
                .build();

        tripService.addDayToTrip(tripId, req);

        Budget updatedBudget = budgetRepository.findByTripId(tripId).orElseThrow();
        Trip updatedTrip = tripRepository.findById(tripId).orElseThrow();
        TripDay day3 = updatedTrip.getDays().stream().filter(d -> d.getDayNumber() == 3).findFirst().orElseThrow();

        // Total budget should increase by newDay.estimatedDayCost
        assertThat(updatedBudget.getTotalBudget()).isEqualTo(700.0 + day3.getEstimatedDayCost());

        // HOTEL & VEHICLE caps must be 100% untouched
        assertThat(updatedBudget.getCategoryBudgets().get(BudgetItem.ItemCategory.HOTEL)).isEqualTo(400.0);
        assertThat(updatedBudget.getCategoryBudgets().get(BudgetItem.ItemCategory.VEHICLE)).isEqualTo(200.0);

        // MISC should absorb the entire added day cost
        assertThat(updatedBudget.getCategoryBudgets().get(BudgetItem.ItemCategory.MISC))
                .isEqualTo(100.0 + day3.getEstimatedDayCost());
    }

    @Test
    @DisplayName("Scenario 3: Remove Last Day on a clean trip")
    void testRemoveLastDayCleanTrip() {
        Trip trip = createTestTrip(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3), 3);
        Long tripId = trip.getId();

        TripDay day3 = trip.getDays().stream().filter(d -> d.getDayNumber() == 3).findFirst().orElseThrow();
        Long day3Id = day3.getId();

        tripService.removeLastDay(tripId, day3Id);

        Trip updatedTrip = tripRepository.findById(tripId).orElseThrow();
        assertThat(updatedTrip.getDays()).hasSize(2);
        assertThat(updatedTrip.getEndDate()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(tripDayRepository.findById(day3Id)).isEmpty();
    }

    @Test
    @DisplayName("Scenario 4A: Guard rejects removal when Vehicle Booking exists on last day")
    void testGuardVehicleBooking() {
        Trip trip = createTestTrip(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), 3);
        Long tripId = trip.getId();
        TripDay day3 = trip.getDays().stream().filter(d -> d.getDayNumber() == 3).findFirst().orElseThrow();

        VehicleBooking vb = VehicleBooking.builder()
                .trip(trip)
                .user(testUser)
                .vehicle(testVehicle)
                .pickupLocation("Colombo")
                .dropoffLocation("Galle")
                .pickupDate(LocalDate.of(2026, 5, 3))
                .dropoffDate(LocalDate.of(2026, 5, 3))
                .status(VehicleBooking.BookingStatus.CONFIRMED)
                .totalCost(100.0)
                .build();
        vehicleBookingRepository.save(vb);

        assertThatThrownBy(() -> tripService.removeLastDay(tripId, day3.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Active vehicle booking exists on 2026-05-03");

        // Verify day 3 was not deleted
        assertThat(tripDayRepository.findById(day3.getId())).isPresent();
    }

    @Test
    @DisplayName("Scenario 4B: Guard rejects removal when Guide Booking exists on last day")
    void testGuardGuideBooking() {
        Trip trip = createTestTrip(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 3);
        Long tripId = trip.getId();
        TripDay day3 = trip.getDays().stream().filter(d -> d.getDayNumber() == 3).findFirst().orElseThrow();

        GuideBooking gb = GuideBooking.builder()
                .trip(trip)
                .user(testUser)
                .guide(testGuide)
                .startDate(LocalDate.of(2026, 6, 3))
                .endDate(LocalDate.of(2026, 6, 3))
                .status(GuideBooking.BookingStatus.CONFIRMED)
                .totalCost(80.0)
                .build();
        guideBookingRepository.save(gb);

        assertThatThrownBy(() -> tripService.removeLastDay(tripId, day3.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Active tour guide booking exists on 2026-06-03");

        assertThat(tripDayRepository.findById(day3.getId())).isPresent();
    }

    @Test
    @DisplayName("Scenario 4C: Guard rejects removal when Hotel Booking exists on last day")
    void testGuardHotelBooking() {
        Trip trip = createTestTrip(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), 3);
        Long tripId = trip.getId();
        TripDay day3 = trip.getDays().stream().filter(d -> d.getDayNumber() == 3).findFirst().orElseThrow();

        HotelBooking hb = HotelBooking.builder()
                .trip(trip)
                .tripDay(day3)
                .hotelApiId("hotel_123")
                .hotelName("Test Hotel")
                .checkIn(LocalDate.of(2026, 7, 3))
                .checkOut(LocalDate.of(2026, 7, 4))
                .status(HotelBooking.BookingStatus.CONFIRMED)
                .totalCost(120.0)
                .build();
        hotelBookingRepository.save(hb);

        assertThatThrownBy(() -> tripService.removeLastDay(tripId, day3.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Active hotel booking exists on 2026-07-03");

        assertThat(tripDayRepository.findById(day3.getId())).isPresent();
    }

    @Test
    @DisplayName("Scenario 4D: Guard rejects removal when BudgetItem expense exists on last day")
    void testGuardBudgetItemExpense() {
        Trip trip = createTestTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 3);
        Long tripId = trip.getId();
        TripDay day3 = trip.getDays().stream().filter(d -> d.getDayNumber() == 3).findFirst().orElseThrow();

        Budget budget = Budget.builder()
                .trip(trip)
                .user(testUser)
                .totalBudget(500.0)
                .currency("USD")
                .items(new ArrayList<>())
                .build();

        BudgetItem expense = BudgetItem.builder()
                .budget(budget)
                .category(BudgetItem.ItemCategory.FOOD)
                .title("Dinner in Galle")
                .amount(45.0)
                .date(LocalDate.of(2026, 8, 3))
                .build();
        budget.getItems().add(expense);
        budgetRepository.save(budget);

        assertThatThrownBy(() -> tripService.removeLastDay(tripId, day3.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Budget expense items exist on 2026-08-03");

        assertThat(tripDayRepository.findById(day3.getId())).isPresent();
    }

    @Test
    @DisplayName("Scenario 5: Attempt to remove day from a 1-day trip is rejected")
    void testRejectRemoveSingleDayTrip() {
        Trip trip = createTestTrip(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 1);
        Long tripId = trip.getId();
        TripDay day1 = trip.getDays().get(0);

        assertThatThrownBy(() -> tripService.removeLastDay(tripId, day1.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Trip must have at least 1 day");
    }

    @Test
    @DisplayName("Scenario 6: Attempt to remove non-last day is rejected")
    void testRejectRemoveNonLastDay() {
        Trip trip = createTestTrip(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 3);
        Long tripId = trip.getId();
        TripDay day1 = trip.getDays().stream().filter(d -> d.getDayNumber() == 1).findFirst().orElseThrow();

        assertThatThrownBy(() -> tripService.removeLastDay(tripId, day1.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only the last day of the trip (Day 3) can be removed");
    }
}
