package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.BudgetLevel;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import com.exploreceylon.backend.model.Location;
import com.exploreceylon.backend.repository.DestinationRepository;
import com.exploreceylon.backend.repository.HiddenGemRepository;
import com.exploreceylon.backend.repository.LocationRepository;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItineraryAssemblyCorridorRedesignTest {

    @Autowired
    private ItineraryAssemblyService itineraryAssemblyService;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private HiddenGemRepository hiddenGemRepository;

    @BeforeEach
    void setUp() {
        // Setup Gazetteer Locations
        locationRepository.save(Location.builder().name("Colombo").latitude(6.9271).longitude(79.8612).build());
        locationRepository.save(Location.builder().name("Ella").latitude(6.8667).longitude(81.0466).build());
        locationRepository.save(Location.builder().name("Kandy").latitude(7.2906).longitude(80.6337).build());

        // Setup Colombo POIs (Start District)
        destinationRepository.save(Destination.builder()
                .name("Gangaramaya Temple").district("Colombo").province("Western")
                .category(DestinationCategory.RELIGIOUS).latitude(6.9167).longitude(79.8569)
                .rating(4.7).reviewCount(200).visitDurationMinutes(60).entryFeeUsd(5.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Colombo National Museum").district("Colombo").province("Western")
                .category(DestinationCategory.CULTURE_HERITAGE).latitude(6.9100).longitude(79.8610)
                .rating(4.6).reviewCount(150).visitDurationMinutes(90).entryFeeUsd(10.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Galle Face Green").district("Colombo").province("Western")
                .category(DestinationCategory.BEACH_COAST).latitude(6.9270).longitude(79.8430)
                .rating(4.5).reviewCount(300).visitDurationMinutes(45).entryFeeUsd(0.0)
                .active(true).build());

        // Setup Intermediate Highway POIs (Kegalle / Ratnapura along corridor)
        destinationRepository.save(Destination.builder()
                .name("Kitulgala White Water Rafting").district("Kegalle").province("Sabaragamuwa")
                .category(DestinationCategory.ADVENTURE).latitude(6.9908).longitude(80.4197)
                .rating(4.7).reviewCount(250).visitDurationMinutes(120).entryFeeUsd(20.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Ratnapura National Museum").district("Ratnapura").province("Sabaragamuwa")
                .category(DestinationCategory.CULTURE_HERITAGE).latitude(6.6828).longitude(80.4036)
                .rating(4.3).reviewCount(80).visitDurationMinutes(60).entryFeeUsd(5.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Bopath Ella Waterfall").district("Ratnapura").province("Sabaragamuwa")
                .category(DestinationCategory.SCENIC_VIEWS).latitude(6.7917).longitude(80.3583)
                .rating(4.4).reviewCount(110).visitDurationMinutes(60).entryFeeUsd(2.0)
                .active(true).build());

        // Setup Destination Zone POIs (Ella / Badulla)
        destinationRepository.save(Destination.builder()
                .name("Nine Arches Bridge").district("Badulla").province("Uva")
                .category(DestinationCategory.SCENIC_VIEWS).latitude(6.8767).longitude(81.0608)
                .rating(4.9).reviewCount(500).visitDurationMinutes(90).entryFeeUsd(0.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Little Adam's Peak").district("Badulla").province("Uva")
                .category(DestinationCategory.ADVENTURE).latitude(6.8611).longitude(81.0547)
                .rating(4.8).reviewCount(450).visitDurationMinutes(120).entryFeeUsd(0.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Ravana Falls").district("Badulla").province("Uva")
                .category(DestinationCategory.SCENIC_VIEWS).latitude(6.8406).longitude(81.0542)
                .rating(4.6).reviewCount(350).visitDurationMinutes(60).entryFeeUsd(0.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Ella Rock").district("Badulla").province("Uva")
                .category(DestinationCategory.ADVENTURE).latitude(6.8500).longitude(81.0400)
                .rating(4.7).reviewCount(380).visitDurationMinutes(120).entryFeeUsd(0.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Lipton's Seat").district("Badulla").province("Uva")
                .category(DestinationCategory.SCENIC_VIEWS).latitude(6.7800).longitude(81.0100)
                .rating(4.8).reviewCount(400).visitDurationMinutes(90).entryFeeUsd(2.0)
                .active(true).build());

        // Setup Kandy POIs for Single District test
        destinationRepository.save(Destination.builder()
                .name("Temple of the Sacred Tooth Relic").district("Kandy").province("Central")
                .category(DestinationCategory.RELIGIOUS).latitude(7.2936).longitude(80.6413)
                .rating(4.9).reviewCount(600).visitDurationMinutes(90).entryFeeUsd(15.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Royal Botanical Gardens Peradeniya").district("Kandy").province("Central")
                .category(DestinationCategory.CULTURE_HERITAGE).latitude(7.2683).longitude(80.5966)
                .rating(4.8).reviewCount(500).visitDurationMinutes(120).entryFeeUsd(10.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Kandy Lake Viewpoint").district("Kandy").province("Central")
                .category(DestinationCategory.SCENIC_VIEWS).latitude(7.2910).longitude(80.6380)
                .rating(4.5).reviewCount(200).visitDurationMinutes(45).entryFeeUsd(0.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Bahirawakanda Buddha Statue").district("Kandy").province("Central")
                .category(DestinationCategory.RELIGIOUS).latitude(7.2970).longitude(80.6300)
                .rating(4.6).reviewCount(250).visitDurationMinutes(60).entryFeeUsd(2.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Udawatta Kele Sanctuary").district("Kandy").province("Central")
                .category(DestinationCategory.ADVENTURE).latitude(7.3000).longitude(80.6400)
                .rating(4.5).reviewCount(180).visitDurationMinutes(90).entryFeeUsd(5.0)
                .active(true).build());

        destinationRepository.save(Destination.builder()
                .name("Hanthana Mountain Range").district("Kandy").province("Central")
                .category(DestinationCategory.SCENIC_VIEWS).latitude(7.2500).longitude(80.6200)
                .rating(4.7).reviewCount(320).visitDurationMinutes(120).entryFeeUsd(0.0)
                .active(true).build());
    }

    @Test
    @DisplayName("Multi-District Trip: Colombo -> Ella (3 Days) strictly excludes starting district (Colombo) places and reaches destination zone")
    void testMultiDistrict_ColomboToElla_ExcludesStartingDistrictPlaces() {
        GeoPoint colombo = itineraryAssemblyService.geocode("Colombo").orElseThrow();
        GeoPoint ella = itineraryAssemblyService.geocode("Ella").orElseThrow();

        List<PlannedDay> days = itineraryAssemblyService.assemble(
                colombo, ella, LocalDate.of(2026, 9, 1), 3, 2, BudgetLevel.MID_RANGE,
                List.of("CULTURE_HERITAGE", "SCENIC_VIEWS", "ADVENTURE")
        );

        assertThat(days).hasSize(3);

        // Multi-district trip going through several districts must strictly exclude all starting district (Colombo) places
        long totalColomboStops = days.stream()
                .flatMap(d -> d.stops().stream())
                .filter(s -> "Colombo".equalsIgnoreCase(s.region()) || s.name().contains("Gangaramaya") || s.name().contains("Colombo") || s.name().contains("Galle Face"))
                .count();
        assertThat(totalColomboStops).isZero();

        // Day 1 should start forward journey with intermediate stops along the corridor (e.g. Ratnapura)
        PlannedDay day1 = days.get(0);
        assertThat(day1.stops()).isNotEmpty();

        // Final Day (Day 3) should have destination zone stops (Ella / Badulla)
        PlannedDay finalDay = days.get(days.size() - 1);
        assertThat(finalDay.stops()).isNotEmpty();
        boolean hasBadullaStops = finalDay.stops().stream()
                .anyMatch(s -> "Badulla".equalsIgnoreCase(s.region()) || s.name().contains("Nine Arches") || s.name().contains("Ravana") || s.name().contains("Little Adam"));
        assertThat(hasBadullaStops).isTrue();
    }

    @Test
    @DisplayName("Single-District Trip: Kandy -> Kandy (2 Days) is unconstrained across Kandy district")
    void testSingleDistrict_KandyToKandy_Unconstrained() {
        GeoPoint kandy = itineraryAssemblyService.geocode("Kandy").orElseThrow();

        List<PlannedDay> days = itineraryAssemblyService.assemble(
                kandy, kandy, LocalDate.of(2026, 9, 1), 2, 2, BudgetLevel.MID_RANGE,
                List.of("CULTURE_HERITAGE", "RELIGIOUS", "SCENIC_VIEWS")
        );

        assertThat(days).hasSize(2);
        // Both days should contain Kandy stops without artificial exclusion
        for (PlannedDay day : days) {
            assertThat(day.stops()).isNotEmpty();
            boolean allKandy = day.stops().stream()
                    .allMatch(s -> "Kandy".equalsIgnoreCase(s.region()) || s.region() == null);
            assertThat(allKandy).isTrue();
        }
    }
}
