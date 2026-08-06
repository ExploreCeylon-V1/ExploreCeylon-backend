package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.PlannerMetadata;
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
class PlannerMetadataRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PlannerMetadataRepository metadataRepository;

    @Test
    @DisplayName("Save and find PlannerMetadata by Trip and Trip ID")
    void testSaveAndFindByTrip() {
        User user = userRepository.save(User.builder().name("Meta User").email("meta@example.com").password("pass").role(User.Role.TRAVELER).build());
        Trip trip = tripRepository.save(Trip.builder()
                .user(user)
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .build());

        PlannerMetadata metadata = PlannerMetadata.builder()
                .trip(trip)
                .qualityScore(95.0)
                .generationTimeMs(15L)
                .plannerVersion("13.0")
                .build();

        PlannerMetadata saved = metadataRepository.save(metadata);
        assertThat(saved.getId()).isNotNull();

        Optional<PlannerMetadata> foundByTrip = metadataRepository.findByTrip(trip);
        assertThat(foundByTrip).isPresent();
        assertThat(foundByTrip.get().getQualityScore()).isEqualTo(95.0);

        Optional<PlannerMetadata> foundById = metadataRepository.findByTripId(trip.getId());
        assertThat(foundById).isPresent();
    }
}
