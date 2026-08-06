package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.PlannerCostSnapshot;
import com.exploreceylon.backend.model.Trip;
import com.exploreceylon.backend.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PlannerCostSnapshotRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PlannerCostSnapshotRepository snapshotRepository;

    @Test
    @DisplayName("Save and find PlannerCostSnapshot by Trip and Trip ID")
    void testSaveAndFindByTrip() {
        User user = userRepository.save(User.builder().name("Cost User").email("cost@example.com").password("pass").role(User.Role.TRAVELER).build());
        Trip trip = tripRepository.save(Trip.builder()
                .user(user)
                .fromLocation("Colombo")
                .toLocation("Galle")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .build());

        PlannerCostSnapshot snapshot = PlannerCostSnapshot.builder()
                .trip(trip)
                .grandTotal(15000.0)
                .transportCost(5000.0)
                .foodCost(4000.0)
                .entranceTicketsCost(6000.0)
                .build();

        PlannerCostSnapshot saved = snapshotRepository.save(snapshot);
        assertThat(saved.getId()).isNotNull();

        Optional<PlannerCostSnapshot> found = snapshotRepository.findByTripId(trip.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getGrandTotal()).isEqualTo(15000.0);
    }
}
