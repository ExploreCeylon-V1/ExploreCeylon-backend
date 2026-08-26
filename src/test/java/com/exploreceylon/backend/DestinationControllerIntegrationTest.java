package com.exploreceylon.backend;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.repository.DestinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coverage for the public (unauthenticated) Destination read endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DestinationControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired DestinationRepository destinations;

    private static final String BASE = "/api/v1/destinations";

    private Long sigiriyaId;

    @BeforeEach
    void setUp() {
        destinations.deleteAll();

        Destination sigiriya = Destination.builder()
                .name("Sigiriya")
                .district("Matale")
                .province("Central")
                .description("Ancient rock fortress")
                .category(Destination.DestinationCategory.CULTURE_HERITAGE)
                .active(true)
                .build();
        sigiriyaId = destinations.save(sigiriya).getId();

        Destination mirissa = Destination.builder()
                .name("Mirissa")
                .district("Matara")
                .province("Southern")
                .description("Popular beach town")
                .category(Destination.DestinationCategory.BEACH_COAST)
                .active(true)
                .build();
        destinations.save(mirissa);
    }

    @Test
    void getAll_noAuth_returnsActiveDestinations() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getById_noAuth_returnsDestination() throws Exception {
        mvc.perform(get(BASE + "/" + sigiriyaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sigiriyaId))
                .andExpect(jsonPath("$.name").value("Sigiriya"))
                .andExpect(jsonPath("$.category").value("CULTURE_HERITAGE"));
    }

    @Test
    void getAll_filteredByCategory_noAuth_returnsMatchingOnly() throws Exception {
        mvc.perform(get(BASE).param("category", "BEACH_COAST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mirissa"));
    }
}
