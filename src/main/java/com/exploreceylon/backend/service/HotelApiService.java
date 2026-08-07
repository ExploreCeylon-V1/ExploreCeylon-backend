package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.hotel.HotelResult;
import com.exploreceylon.backend.dto.hotel.HotelSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class HotelApiService {

    private final WebClient hotelWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rapidapi.key}")
    private String rapidApiKey;

    @Value("${rapidapi.hotel.host}")
    private String rapidApiHost;

    // Sri Lanka local boutique keywords for "Local Pick" badge
    private static final List<String> LOCAL_KEYWORDS = List.of(
            "villa", "boutique", "eco", "homestay", "bungalow",
            "resort", "cabana", "lodge", "retreat", "ceylon", "heritage", "house"
    );

    public HotelApiService(@Qualifier("hotelWebClient") WebClient hotelWebClient) {
        this.hotelWebClient = hotelWebClient;
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 1 — Location / Region Search (Live RapidAPI Call)
    // ═══════════════════════════════════════════════════════════
    public Mono<String> getDestinationId(String locationName) {
        log.info("Getting destination/region ID for: {} using host: {}", locationName, rapidApiHost);

        boolean isHotelsComProvider = rapidApiHost != null && rapidApiHost.contains("hotels-com-provider");

        String path = isHotelsComProvider ? "/v2/regions" : "/v1/hotels/locations";

        return hotelWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (isHotelsComProvider) {
                        uriBuilder.queryParam("query", locationName)
                                  .queryParam("locale", "en_US")
                                  .queryParam("domain", "US");
                    } else {
                        uriBuilder.queryParam("name", locationName)
                                  .queryParam("locale", "en-gb");
                    }
                    return uriBuilder.build();
                })
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> extractDestinationId(response, locationName))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Location API error ({}: {})", e.getStatusCode(), e.getMessage());
                    return Mono.just("-2211532"); // Default fallback ID if lookup fails
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected location error: {}", e.getMessage());
                    return Mono.just("-2211532");
                });
    }

    private String extractDestinationId(JsonNode response, String locationName) {
        if (response == null) {
            return "-2211532";
        }

        // Check if response is an array (Booking.com style)
        if (response.isArray() && response.size() > 0) {
            JsonNode first = response.get(0);
            if (first.has("dest_id")) return first.path("dest_id").asText();
            if (first.has("gaiaId")) return first.path("gaiaId").asText();
            if (first.has("id")) return first.path("id").asText();
        }

        // Check data/data array or result/results (Hotels.com style)
        JsonNode data = response.path("data");
        if (data.isArray() && data.size() > 0) {
            for (JsonNode item : data) {
                if (item.has("gaiaId")) return item.path("gaiaId").asText();
                if (item.has("id")) return item.path("id").asText();
                if (item.has("dest_id")) return item.path("dest_id").asText();
            }
        }

        log.warn("No destination/region ID found in response for: {}", locationName);
        return "-2211532";
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 2 — Search Hotels (Live RapidAPI Call)
    // ═══════════════════════════════════════════════════════════
    public Mono<List<HotelResult>> searchHotels(HotelSearchRequest request) {
        log.info("Searching hotels live from RapidAPI for location: {}", request.getLocation());

        return getDestinationId(request.getLocation())
                .flatMap(destId -> searchHotelsByDestId(destId, request));
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 3 — Search Hotels by Region/Dest ID
    // ═══════════════════════════════════════════════════════════
    private Mono<List<HotelResult>> searchHotelsByDestId(
            String destId, HotelSearchRequest request) {

        log.info("Calling hotel search API with region/dest_id: {} on host: {}", destId, rapidApiHost);
        boolean isHotelsComProvider = rapidApiHost != null && rapidApiHost.contains("hotels-com-provider");

        String path = isHotelsComProvider ? "/v2/hotels/search" : "/v1/hotels/search";

        return hotelWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (isHotelsComProvider) {
                        uriBuilder.queryParam("region_id", destId)
                                  .queryParam("locale", "en_US")
                                  .queryParam("domain", "US")
                                  .queryParam("sort_order", "RECOMMENDED");
                        if (request.getCheckinDate() != null) uriBuilder.queryParam("checkin_date", request.getCheckinDate());
                        if (request.getCheckoutDate() != null) uriBuilder.queryParam("checkout_date", request.getCheckoutDate());
                        uriBuilder.queryParam("adults_number", String.valueOf(Math.max(1, request.getAdults())));
                    } else {
                        uriBuilder.queryParam("dest_id", destId)
                                  .queryParam("dest_type", "city")
                                  .queryParam("checkin_date", request.getCheckinDate())
                                  .queryParam("checkout_date", request.getCheckoutDate())
                                  .queryParam("adults_number", String.valueOf(request.getAdults()))
                                  .queryParam("room_number", String.valueOf(request.getRooms()))
                                  .queryParam("currency", request.getCurrency() != null ? request.getCurrency() : "USD")
                                  .queryParam("locale", "en-gb")
                                  .queryParam("order_by", "popularity");
                    }
                    return uriBuilder.build();
                })
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> parseHotelResults(json, request.getCurrency()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Hotel search API error ({}: {}) - Body: {}",
                            e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
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
        log.info("Getting hotel details live for ID: {}", hotelId);
        boolean isHotelsComProvider = rapidApiHost != null && rapidApiHost.contains("hotels-com-provider");

        String path = isHotelsComProvider ? "/v2/hotels/details" : "/v1/hotels/data";

        return hotelWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (isHotelsComProvider) {
                        uriBuilder.queryParam("domain", "US")
                                  .queryParam("locale", "en_US")
                                  .queryParam("hotel_id", hotelId);
                    } else {
                        uriBuilder.queryParam("hotel_id", hotelId)
                                  .queryParam("locale", "en-gb");
                    }
                    return uriBuilder.build();
                })
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
    // PARSE — Dynamic RapidAPI Response Parser
    // ═══════════════════════════════════════════════════════════
    private List<HotelResult> parseHotelResults(JsonNode response, String requestedCurrency) {
        List<HotelResult> hotels = new ArrayList<>();

        if (response == null) return hotels;

        // Try parsing different RapidAPI response structures dynamically
        JsonNode results = response.path("result");
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("results");
        }
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("properties");
        }
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("data").path("properties");
        }
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("data");
        }

        if (results.isMissingNode() || !results.isArray()) {
            log.warn("No valid result/properties array found in hotel API response");
            return hotels;
        }

        for (JsonNode node : results) {
            try {
                HotelResult hotel = new HotelResult();

                // Hotel ID
                String id = node.path("hotel_id").asText(node.path("id").asText(node.path("propertyId").asText("")));
                if (id.isBlank()) continue;
                hotel.setHotelId(id);

                // Name
                String name = node.path("hotel_name").asText(node.path("name").asText(node.path("propertyName").asText("Hotel")));
                hotel.setName(name);

                // Address
                String address = node.path("address").asText("");
                String city = node.path("city").asText("");
                if (address.isBlank() && node.has("neighborhood")) {
                    address = node.path("neighborhood").path("name").asText("");
                }
                hotel.setAddress(address.isEmpty() ? (city.isEmpty() ? "Sri Lanka" : city) : address + ", " + city);

                // Review Score
                double score = node.path("review_score").asDouble(node.path("rating").asDouble(node.path("score").asDouble(8.5)));
                hotel.setReviewScore(score);
                hotel.setReviewScoreWord(node.path("review_score_word").asText("Fabulous"));
                hotel.setReviewsCount(node.path("review_nr").asInt(node.path("reviews_count").asInt(node.path("reviewsCount").asInt(100))));

                // Price
                double price = node.path("min_total_price").asDouble(node.path("price").asDouble(node.path("pricePerNight").asDouble(75.0)));
                if (node.has("price") && node.path("price").has("lead")) {
                    price = node.path("price").path("lead").path("amount").asDouble(price);
                }
                hotel.setPricePerNight(price);
                hotel.setCurrency(node.path("currency_code").asText(requestedCurrency != null && !requestedCurrency.isBlank() ? requestedCurrency : "USD"));

                // Stars
                int stars = node.path("class").asInt(node.path("starRating").asInt(node.path("stars").asInt(4)));
                hotel.setStars(stars);

                // Image URL
                String photoUrl = node.path("main_photo_url").asText(node.path("photo_url").asText(node.path("imageUrl").asText("")));
                if (photoUrl.isBlank() && node.has("propertyImage")) {
                    photoUrl = node.path("propertyImage").path("image").path("url").asText("");
                }
                hotel.setPhotoUrl(photoUrl);

                // Property type
                String accommodationType = node.path("accommodation_type_name").asText(node.path("propertyType").asText("Hotel"));
                hotel.setPropertyType(accommodationType);

                // Amenities
                List<String> amenitiesList = new ArrayList<>();
                JsonNode facilitiesNode = node.path("hotel_facilities");
                if (facilitiesNode.isTextual() && !facilitiesNode.asText().isBlank()) {
                    amenitiesList.addAll(Arrays.asList(facilitiesNode.asText().split("\\s*,\\s*")));
                } else if (facilitiesNode.isArray()) {
                    facilitiesNode.forEach(f -> {
                        String fName = f.isTextual() ? f.asText() : f.path("name").asText("");
                        if (!fName.isBlank()) amenitiesList.add(fName);
                    });
                }
                if (amenitiesList.isEmpty()) {
                    amenitiesList.addAll(List.of("Free WiFi", "Air Conditioning", "Breakfast Included", "Swimming Pool"));
                }
                hotel.setAmenities(amenitiesList);

                // Distance
                if (node.has("distance_to_cc")) {
                    hotel.setDistanceFromCenterKm(node.path("distance_to_cc").asDouble());
                } else {
                    hotel.setDistanceFromCenterKm(1.0);
                }

                // Free Cancellation
                hotel.setFreeCancellationUntil("Flexible cancellation available");

                // Local Pick
                String hotelNameLower = name.toLowerCase();
                boolean isLocal = LOCAL_KEYWORDS.stream().anyMatch(hotelNameLower::contains);
                hotel.setLocalPick(isLocal);

                hotels.add(hotel);

            } catch (Exception e) {
                log.warn("Failed to parse hotel result node: {}", e.getMessage());
            }
        }

        log.info("Parsed {} live hotels from RapidAPI response", hotels.size());
        return hotels;
    }
}