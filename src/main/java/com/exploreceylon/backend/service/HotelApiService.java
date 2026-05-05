package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.hotel.HotelResult;
import com.exploreceylon.backend.dto.hotel.HotelSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class HotelApiService {

    private final WebClient hotelWebClient;

    @Value("${rapidapi.key}")
    private String rapidApiKey;

    @Value("${rapidapi.hotel.host}")
    private String rapidApiHost;

    // Sri Lanka local boutique keywords for "Local Pick" badge
    private static final List<String> LOCAL_KEYWORDS = List.of(
            "villa", "boutique", "eco", "homestay", "bungalow",
            "resort", "cabana", "lodge", "retreat", "ceylon"
    );

    public HotelApiService(@Qualifier("hotelWebClient") WebClient hotelWebClient) {
        this.hotelWebClient = hotelWebClient;
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 1 — Location Name → dest_id
    // ═══════════════════════════════════════════════════════════
    public Mono<String> getDestinationId(String locationName) {
        log.info("Getting destination ID for: {}", locationName);

        return hotelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/hotels/locations")
                        .queryParam("name", locationName)
                        .queryParam("locale", "en-gb")
                        .build())
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    if (response.isArray() && response.size() > 0) {
                        String destId = response.get(0).path("dest_id").asText();
                        log.info("Found dest_id: {} for location: {}", destId, locationName);
                        return destId;
                    }
                    log.warn("No dest_id found for: {}, using Colombo default", locationName);
                    return "-2211532"; // Default: Colombo
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Location API error: {} - {}", e.getStatusCode(), e.getMessage());
                    return Mono.just("-2211532"); // fallback to Colombo
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected location error: {}", e.getMessage());
                    return Mono.just("-2211532");
                });
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 2 — Search Hotels (Public Method)
    // ═══════════════════════════════════════════════════════════
    public Mono<List<HotelResult>> searchHotels(HotelSearchRequest request) {
        log.info("Searching hotels for location: {}", request.getLocation());

        return getDestinationId(request.getLocation())
                .flatMap(destId -> searchHotelsByDestId(destId, request));
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 3 — Search Hotels by dest_id (Private Method)
    // ═══════════════════════════════════════════════════════════
    private Mono<List<HotelResult>> searchHotelsByDestId(
            String destId, HotelSearchRequest request) {

        log.info("Calling hotel search API with dest_id: {}", destId);

        return hotelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/hotels/search")
                        .queryParam("dest_id", destId)
                        .queryParam("dest_type", "city")
                        .queryParam("checkin_date", request.getCheckinDate())
                        .queryParam("checkout_date", request.getCheckoutDate())
                        .queryParam("adults_number",
                                String.valueOf(request.getAdults()))
                        .queryParam("room_number",
                                String.valueOf(request.getRooms()))
                        .queryParam("currency", "USD")
                        .queryParam("filter_by_currency", "USD")
                        .queryParam("locale", "en-gb")
                        .queryParam("order_by", "popularity")
                        .queryParam("units", "metric")
                        .queryParam("include_adjacency", "true")
                        .build())
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseHotelResults)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Hotel search API error: {} - Body: {}",
                            e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.just(Collections.emptyList());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected hotel search error: {}", e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 4 — Get Hotel Details by ID
    // ═══════════════════════════════════════════════════════════
    public Mono<JsonNode> getHotelDetails(String hotelId) {
        log.info("Getting hotel details for ID: {}", hotelId);

        return hotelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/hotels/data")
                        .queryParam("hotel_id", hotelId)
                        .queryParam("locale", "en-gb")
                        .build())
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Hotel details API error: {}", e.getStatusCode());
                    return Mono.empty();
                });
    }

    // ═══════════════════════════════════════════════════════════
    // PARSE — API Response → HotelResult List
    // ═══════════════════════════════════════════════════════════
    private List<HotelResult> parseHotelResults(JsonNode response) {
        List<HotelResult> hotels = new ArrayList<>();

        // Log raw response for debugging
        log.debug("Raw API response: {}", response.toString().substring(
                0, Math.min(200, response.toString().length())));

        JsonNode results = response.path("result");

        if (results.isMissingNode() || !results.isArray()) {
            log.warn("No 'result' array found in hotel API response");
            return hotels;
        }

        for (JsonNode node : results) {
            try {
                HotelResult hotel = new HotelResult();

                hotel.setHotelId(node.path("hotel_id").asText());
                hotel.setName(node.path("hotel_name").asText());

                // Address — combine address + city
                String address = node.path("address").asText("");
                String city = node.path("city").asText("");
                hotel.setAddress(address.isEmpty() ? city : address + ", " + city);

                hotel.setReviewScore(node.path("review_score").asDouble(0));
                hotel.setReviewScoreWord(node.path("review_score_word").asText(""));
                hotel.setPricePerNight(node.path("min_total_price").asDouble(0));
                hotel.setCurrency(node.path("currency_code").asText("USD"));
                hotel.setStars(node.path("class").asInt(0));
                hotel.setPhotoUrl(node.path("main_photo_url").asText(""));

                // Local Pick badge
                String hotelName = hotel.getName().toLowerCase();
                boolean isLocal = LOCAL_KEYWORDS.stream()
                        .anyMatch(hotelName::contains);
                hotel.setLocalPick(isLocal);

                hotels.add(hotel);

            } catch (Exception e) {
                log.warn("Failed to parse hotel node: {}", e.getMessage());
            }
        }

        log.info("Parsed {} hotels successfully", hotels.size());
        return hotels;
    }
}