package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.hotel.HotelResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HotelApiServiceTest {

    private HotelApiService hotelApiService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder().baseUrl("https://dummy.com").build();
        hotelApiService = new HotelApiService(webClient);
        ReflectionTestUtils.setField(hotelApiService, "rapidApiHost", "booking-com15.p.rapidapi.com");
        ReflectionTestUtils.setField(hotelApiService, "rapidApiKey", "test-key");
        hotelApiService.initFallbackHotels();
    }

    @Test
    void testFallbackHotelsLoaded() {
        List<HotelResult> colomboHotels = hotelApiService.getCuratedFallbackHotels("Colombo, Sri Lanka", "USD");
        assertNotNull(colomboHotels);
        assertFalse(colomboHotels.isEmpty());
        assertTrue(colomboHotels.size() >= 3);
        assertEquals("USD", colomboHotels.get(0).getCurrency());
        assertTrue(colomboHotels.get(0).getName().contains("Galle Face Hotel") ||
                   colomboHotels.get(0).getName().contains("Colombo"));
    }

    @Test
    void testFallbackHotelsKandy() {
        List<HotelResult> kandyHotels = hotelApiService.getCuratedFallbackHotels("Kandy", "USD");
        assertNotNull(kandyHotels);
        assertFalse(kandyHotels.isEmpty());
        assertTrue(kandyHotels.stream().anyMatch(h -> h.getName().toLowerCase().contains("kandyan") || h.getAddress().toLowerCase().contains("kandy")));
    }

    @Test
    void testFallbackHotelsGalle() {
        List<HotelResult> galleHotels = hotelApiService.getCuratedFallbackHotels("Galle Fort, Sri Lanka", "USD");
        assertNotNull(galleHotels);
        assertFalse(galleHotels.isEmpty());
        assertTrue(galleHotels.stream().anyMatch(h -> h.getName().toLowerCase().contains("amangalla") || h.getName().toLowerCase().contains("lighthouse") || h.getAddress().toLowerCase().contains("galle")));
    }

    @Test
    void testCurrencyConversionLKR() {
        List<HotelResult> lkrHotels = hotelApiService.getCuratedFallbackHotels("Colombo", "LKR");
        assertNotNull(lkrHotels);
        assertEquals("LKR", lkrHotels.get(0).getCurrency());
        assertTrue(lkrHotels.get(0).getPricePerNight() > 10000.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testParseBooking15Response() throws Exception {
        String booking15SampleJson = "{\"status\":true,\"data\":{\"hotels\":[{\"hotel_id\":3765351,\"accessibilityLabel\":\"Marino Beach Colombo. 5 stars. 9.2 Superb. 3.8 km from centre. Free cancellation.\",\"property\":{\"id\":3765351,\"name\":\"Marino Beach Colombo\",\"wishlistName\":\"Colombo\",\"reviewScore\":9.2,\"reviewScoreWord\":\"Superb\",\"reviewCount\":9917,\"accuratePropertyClass\":5,\"photoUrls\":[\"https://cf.bstatic.com/test.jpg\"],\"priceBreakdown\":{\"grossPrice\":{\"value\":196.65,\"currency\":\"USD\"}}}}]}}";

        JsonNode responseNode = objectMapper.readTree(booking15SampleJson);
        Method parseMethod = HotelApiService.class.getDeclaredMethod("parseHotelResults", JsonNode.class, String.class);
        parseMethod.setAccessible(true);
        List<HotelResult> parsed = (List<HotelResult>) parseMethod.invoke(hotelApiService, responseNode, "USD");

        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        HotelResult h = parsed.get(0);
        assertEquals("3765351", h.getHotelId());
        assertEquals("Marino Beach Colombo", h.getName());
        assertEquals("Colombo, Sri Lanka", h.getAddress());
        assertEquals(9.2, h.getReviewScore());
        assertEquals("Superb", h.getReviewScoreWord());
        assertEquals(9917, h.getReviewsCount());
        assertEquals(5, h.getStars());
        assertEquals(196.65, h.getPricePerNight());
        assertEquals("USD", h.getCurrency());
        assertEquals("https://cf.bstatic.com/test.jpg", h.getPhotoUrl());
        assertEquals("Free cancellation available", h.getFreeCancellationUntil());
    }
}
