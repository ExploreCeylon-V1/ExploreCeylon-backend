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

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HotelApiService {

    private final WebClient hotelWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<HotelResult> fallbackHotels = new ArrayList<>();

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

    @PostConstruct
    public void initFallbackHotels() {
        try (InputStream is = getClass().getResourceAsStream("/data/fallback_hotels.json")) {
            if (is != null) {
                objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                fallbackHotels = objectMapper.readValue(is, new TypeReference<List<HotelResult>>() {});
                log.info("Loaded {} curated Sri Lanka fallback hotels from classpath", fallbackHotels.size());
            } else {
                log.warn("Fallback hotels JSON file /data/fallback_hotels.json not found in classpath");
            }
        } catch (Exception e) {
            log.error("Failed to load curated fallback hotels dataset: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 1 — Location / Region Search (Live RapidAPI Call)
    // ═══════════════════════════════════════════════════════════
    public Mono<String> getDestinationId(String locationName) {
        log.info("Getting destination/region ID for: {} using host: {}", locationName, rapidApiHost);

        boolean isBooking15 = rapidApiHost != null && (rapidApiHost.contains("booking-com15") || rapidApiHost.contains("booking"));
        boolean isHotelsComProvider = rapidApiHost != null && rapidApiHost.contains("hotels-com-provider");

        String path;
        if (isBooking15) {
            path = "/api/v1/hotels/searchDestination";
        } else if (isHotelsComProvider) {
            path = "/v2/regions";
        } else {
            path = "/v1/hotels/locations";
        }

        return hotelWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (isBooking15) {
                        uriBuilder.queryParam("query", locationName);
                    } else if (isHotelsComProvider) {
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
                    log.error("Booking-com15 destination lookup failed, status={}, body={}, exception={}",
                            e.getStatusCode(), e.getResponseBodyAsString(), e.getMessage());
                    return Mono.just("-2214877"); // Default Colombo ID if lookup fails
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected location error: {}", e.getMessage(), e);
                    return Mono.just("-2214877");
                });
    }

    private String extractDestinationId(JsonNode response, String locationName) {
        if (response == null) {
            return "-2214877";
        }

        // Check if response is an array (Booking.com style)
        if (response.isArray() && response.size() > 0) {
            JsonNode first = response.get(0);
            if (first.has("dest_id")) return first.path("dest_id").asText();
            if (first.has("gaiaId")) return first.path("gaiaId").asText();
            if (first.has("id")) return first.path("id").asText();
        }

        // Check data array (booking-com15 / Hotels.com style)
        JsonNode data = response.path("data");
        if (data.isArray() && data.size() > 0) {
            for (JsonNode item : data) {
                if (item.has("dest_id")) return item.path("dest_id").asText();
                if (item.has("gaiaId")) return item.path("gaiaId").asText();
                if (item.has("id")) return item.path("id").asText();
            }
        }

        log.warn("No destination/region ID found in response for: {}", locationName);
        return "-2214877";
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 2 — Search Hotels (Live RapidAPI Call with Curated Fallback)
    // ═══════════════════════════════════════════════════════════
    public Mono<List<HotelResult>> searchHotels(HotelSearchRequest request) {
        log.info("Searching hotels live from RapidAPI for location: {}", request.getLocation());

        return getDestinationId(request.getLocation())
                .flatMap(destId -> searchHotelsByDestId(destId, request))
                .map(hotels -> {
                    if (hotels == null || hotels.isEmpty()) {
                        log.warn("RapidAPI returned no results for location: '{}'. Serving curated Sri Lanka fallback dataset.",
                                request.getLocation());
                        return getCuratedFallbackHotels(request.getLocation(), request.getCurrency());
                    }
                    return hotels;
                })
                .onErrorResume(e -> {
                    log.warn("RapidAPI search encountered exception ({}: '{}'). Serving curated Sri Lanka fallback dataset.",
                            e.getClass().getSimpleName(), e.getMessage());
                    return Mono.just(getCuratedFallbackHotels(request.getLocation(), request.getCurrency()));
                });
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 3 — Search Hotels by Region/Dest ID
    // ═══════════════════════════════════════════════════════════
    private Mono<List<HotelResult>> searchHotelsByDestId(
            String destId, HotelSearchRequest request) {

        log.info("Calling hotel search API with dest_id: {} on host: {}", destId, rapidApiHost);
        boolean isBooking15 = rapidApiHost != null && (rapidApiHost.contains("booking-com15") || rapidApiHost.contains("booking"));
        boolean isHotelsComProvider = rapidApiHost != null && rapidApiHost.contains("hotels-com-provider");

        String path;
        if (isBooking15) {
            path = "/api/v1/hotels/searchHotels";
        } else if (isHotelsComProvider) {
            path = "/v2/hotels/search";
        } else {
            path = "/v1/hotels/search";
        }

        return hotelWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (isBooking15) {
                        uriBuilder.queryParam("dest_id", destId)
                                  .queryParam("search_type", "city");
                        if (request.getCheckinDate() != null) uriBuilder.queryParam("arrival_date", request.getCheckinDate());
                        if (request.getCheckoutDate() != null) uriBuilder.queryParam("departure_date", request.getCheckoutDate());
                        uriBuilder.queryParam("adults", String.valueOf(Math.max(1, request.getAdults())));
                        uriBuilder.queryParam("room_qty", String.valueOf(Math.max(1, request.getRooms())));
                        uriBuilder.queryParam("currency_code", request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency() : "USD");
                        uriBuilder.queryParam("page_number", "1");
                    } else if (isHotelsComProvider) {
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
                    log.error("Booking-com15 call failed, falling back. status={}, body={}, exception={}",
                            e.getStatusCode(), e.getResponseBodyAsString(), e.getMessage());
                    return Mono.just(Collections.emptyList());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected hotel search error: {}", e.getMessage(), e);
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * Serves curated Sri Lanka hotels filtered by location keyword.
     * Guaranteed to match the exact HotelResult DTO schema.
     */
    public List<HotelResult> getCuratedFallbackHotels(String location, String requestedCurrency) {
        if (fallbackHotels.isEmpty()) {
            initFallbackHotels();
        }

        String locFilter = location != null ? location.toLowerCase().trim() : "";
        List<HotelResult> matched = fallbackHotels.stream()
                .filter(h -> {
                    if (locFilter.isEmpty() || locFilter.equals("sri lanka") || locFilter.equals("ceylon")) {
                        return true;
                    }
                    String addr = h.getAddress() != null ? h.getAddress().toLowerCase() : "";
                    String name = h.getName() != null ? h.getName().toLowerCase() : "";
                    // Check if city name exists in search query or address
                    for (String city : List.of("colombo", "kandy", "galle", "ella", "sigiriya", "dambulla", "nuwara eliya", "bentota", "mirissa", "yala", "tangalle")) {
                        if (locFilter.contains(city) && (addr.contains(city) || name.contains(city))) {
                            return true;
                        }
                    }
                    return addr.contains(locFilter) || name.contains(locFilter);
                })
                .map(h -> cloneWithCurrency(h, requestedCurrency))
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            // Return full curated set if no specific destination matched
            matched = fallbackHotels.stream()
                    .map(h -> cloneWithCurrency(h, requestedCurrency))
                    .collect(Collectors.toList());
        }

        log.info("Delivering {} curated Sri Lanka hotels for location: '{}'", matched.size(), location);
        return matched;
    }

    private HotelResult cloneWithCurrency(HotelResult src, String targetCurrency) {
        HotelResult copy = new HotelResult();
        copy.setHotelId(src.getHotelId());
        copy.setName(src.getName());
        copy.setAddress(src.getAddress());
        copy.setReviewScore(src.getReviewScore());
        copy.setReviewScoreWord(src.getReviewScoreWord());
        copy.setReviewsCount(src.getReviewsCount());
        copy.setStars(src.getStars());
        copy.setPhotoUrl(src.getPhotoUrl());
        copy.setLocalPick(src.isLocalPick());
        copy.setPropertyType(src.getPropertyType());
        copy.setAmenities(src.getAmenities() != null ? new ArrayList<>(src.getAmenities()) : new ArrayList<>());
        copy.setDistanceFromCenterKm(src.getDistanceFromCenterKm());
        copy.setFreeCancellationUntil(src.getFreeCancellationUntil());

        String cur = (targetCurrency != null && !targetCurrency.isBlank()) ? targetCurrency.toUpperCase() : "USD";
        copy.setCurrency(cur);

        // Standard approximate conversion for display if non-USD requested
        if ("LKR".equalsIgnoreCase(cur)) {
            copy.setPricePerNight(Math.round(src.getPricePerNight() * 305.0));
        } else if ("EUR".equalsIgnoreCase(cur)) {
            copy.setPricePerNight(Math.round(src.getPricePerNight() * 0.92 * 100.0) / 100.0);
        } else if ("GBP".equalsIgnoreCase(cur)) {
            copy.setPricePerNight(Math.round(src.getPricePerNight() * 0.78 * 100.0) / 100.0);
        } else {
            copy.setPricePerNight(src.getPricePerNight());
        }

        return copy;
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 4 — Get Hotel Details by ID
    // ═══════════════════════════════════════════════════════════
    public Mono<JsonNode> getHotelDetails(String hotelId) {
        log.info("Getting hotel details live for ID: {}", hotelId);

        if (hotelId != null && hotelId.startsWith("sl-")) {
            if (fallbackHotels.isEmpty()) initFallbackHotels();
            HotelResult match = fallbackHotels.stream()
                    .filter(h -> hotelId.equals(h.getHotelId()))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                return Mono.just(objectMapper.valueToTree(match));
            }
        }

        boolean isBooking15 = rapidApiHost != null && (rapidApiHost.contains("booking-com15") || rapidApiHost.contains("booking"));
        boolean isHotelsComProvider = rapidApiHost != null && rapidApiHost.contains("hotels-com-provider");

        String path;
        if (isBooking15) {
            path = "/api/v1/hotels/getDescriptionAndInfo";
        } else if (isHotelsComProvider) {
            path = "/v2/hotels/details";
        } else {
            path = "/v1/hotels/data";
        }

        return hotelWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    if (isBooking15) {
                        uriBuilder.queryParam("hotel_id", hotelId);
                    } else if (isHotelsComProvider) {
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
    // PARSE — Dynamic RapidAPI Response Parser (Multi-Provider Support)
    // ═══════════════════════════════════════════════════════════
    private List<HotelResult> parseHotelResults(JsonNode response, String requestedCurrency) {
        List<HotelResult> hotels = new ArrayList<>();

        if (response == null) return hotels;

        // Try parsing different RapidAPI response structures dynamically
        JsonNode results = null;

        // 1. booking-com15 schema: response -> { "data": { "hotels": [ { "property": { ... } } ] } }
        if (response.has("data") && response.path("data").has("hotels") && response.path("data").path("hotels").isArray()) {
            results = response.path("data").path("hotels");
        }

        // 2. Booking.com / generic v1
        if (results == null || results.isMissingNode() || !results.isArray()) {
            results = response.path("result");
        }
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("results");
        }
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("properties");
        }
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("data").path("properties");
        }
        // 3. hotels-com-provider v2 API:
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("data").path("propertySearch").path("properties");
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
                JsonNode prop = node.has("property") ? node.path("property") : node;

                // Hotel ID — booking-com15: prop.id / node.hotel_id | v1: hotel_id | v2: id
                String id = prop.path("id").asText(node.path("hotel_id").asText(node.path("id").asText(node.path("propertyId").asText(""))));
                if (id.isBlank()) continue;
                hotel.setHotelId(id);

                // Name — booking-com15: prop.name | v1: hotel_name | v2: name
                String name = prop.path("name").asText(node.path("hotel_name").asText(node.path("name").asText(node.path("propertyName").asText("Hotel"))));
                hotel.setName(name);

                // Address — booking-com15: prop.wishlistName | v1: address+city | v2: neighborhood.name
                String address = prop.path("wishlistName").asText("");
                if (address.isBlank()) {
                    address = node.path("address").asText("");
                    String city = node.path("city").asText("");
                    if (address.isBlank() && node.has("neighborhood")) {
                        address = node.path("neighborhood").path("name").asText("");
                    }
                    if (!city.isBlank() && !address.contains(city)) {
                        address = address.isBlank() ? city : address + ", " + city;
                    }
                }
                if (!address.isBlank() && !address.toLowerCase().contains("sri lanka")) {
                    address = address + ", Sri Lanka";
                }
                hotel.setAddress(address.isBlank() ? "Sri Lanka" : address);

                // Review Score — booking-com15: prop.reviewScore | v1: review_score | v2: reviews.score
                double score = prop.path("reviewScore").asDouble(
                        node.path("review_score").asDouble(
                        node.path("rating").asDouble(
                        node.path("score").asDouble(0.0))));
                if (score == 0.0 && node.has("reviews")) {
                    score = node.path("reviews").path("score").asDouble(8.5);
                }
                hotel.setReviewScore(score == 0.0 ? 8.5 : Math.round(score * 10.0) / 10.0);

                // Review score word — booking-com15: prop.reviewScoreWord | v1: review_score_word | v2: reviews.localizedAdvisory
                String scoreWord = prop.path("reviewScoreWord").asText(node.path("review_score_word").asText(""));
                if (scoreWord.isBlank() && node.has("reviews")) {
                    scoreWord = node.path("reviews").path("localizedAdvisory").asText("Fabulous");
                }
                hotel.setReviewScoreWord(scoreWord.isBlank() ? "Fabulous" : scoreWord);

                // Reviews count — booking-com15: prop.reviewCount | v1: review_nr | v2: reviews.total
                int reviewsCount = prop.path("reviewCount").asInt(
                        node.path("review_nr").asInt(
                        node.path("reviews_count").asInt(
                        node.path("reviewsCount").asInt(0))));
                if (reviewsCount == 0 && node.has("reviews")) {
                    reviewsCount = node.path("reviews").path("total").asInt(100);
                }
                hotel.setReviewsCount(reviewsCount == 0 ? 100 : reviewsCount);

                // Price — booking-com15: prop.priceBreakdown.grossPrice.value | v1: min_total_price | v2: price.lead.amount
                double price = 75.0;
                if (prop.has("priceBreakdown") && prop.path("priceBreakdown").has("grossPrice")) {
                    price = prop.path("priceBreakdown").path("grossPrice").path("value").asDouble(price);
                } else if (node.has("price") && node.path("price").has("lead")) {
                    price = node.path("price").path("lead").path("amount").asDouble(price);
                } else {
                    price = node.path("min_total_price").asDouble(node.path("pricePerNight").asDouble(price));
                }
                hotel.setPricePerNight(Math.round(price * 100.0) / 100.0);

                // Currency — booking-com15: prop.priceBreakdown.grossPrice.currency | v1: currency_code
                String currency = "";
                if (prop.has("priceBreakdown") && prop.path("priceBreakdown").has("grossPrice")) {
                    currency = prop.path("priceBreakdown").path("grossPrice").path("currency").asText("");
                } else if (prop.has("currency")) {
                    currency = prop.path("currency").asText("");
                } else if (node.has("currency_code")) {
                    currency = node.path("currency_code").asText("");
                } else if (node.has("price") && node.path("price").has("lead")) {
                    currency = node.path("price").path("lead").path("currencyInfo").path("code").asText("");
                }
                hotel.setCurrency(currency.isBlank()
                        ? (requestedCurrency != null && !requestedCurrency.isBlank() ? requestedCurrency : "USD")
                        : currency);

                // Stars — booking-com15: prop.accuratePropertyClass / prop.propertyClass | v1: class | v2: star
                int stars = prop.path("accuratePropertyClass").asInt(
                        prop.path("propertyClass").asInt(
                        node.path("class").asInt(
                        node.path("star").asInt(
                        node.path("starRating").asInt(
                        node.path("stars").asInt(0))))));
                hotel.setStars(stars == 0 ? 4 : stars);

                // Image URL — booking-com15: prop.photoUrls[0] | v1: main_photo_url | v2: propertyImage.image.url
                String photoUrl = "";
                if (prop.has("photoUrls") && prop.path("photoUrls").isArray() && prop.path("photoUrls").size() > 0) {
                    photoUrl = prop.path("photoUrls").get(0).asText("");
                } else {
                    photoUrl = node.path("main_photo_url").asText(node.path("photo_url").asText(node.path("imageUrl").asText("")));
                    if (photoUrl.isBlank() && node.has("propertyImage")) {
                        photoUrl = node.path("propertyImage").path("image").path("url").asText("");
                    }
                }
                if (photoUrl.isBlank()) {
                    photoUrl = "https://images.unsplash.com/photo-1566073771259-6a8506099945?q=80&w=800&auto=format&fit=crop";
                }
                hotel.setPhotoUrl(photoUrl);

                // Property type — booking-com15: prop.propertyType | v1: accommodation_type_name
                String accommodationType = prop.path("propertyType").asText(node.path("accommodation_type_name").asText(node.path("propertyType").asText("Hotel")));
                hotel.setPropertyType(accommodationType);

                // Amenities — default luxury Sri Lanka amenities if not provided
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

                // Distance from city center — booking-com15: parsed from accessibilityLabel
                String accessLabel = node.path("accessibilityLabel").asText("");
                Double dist = 1.0;
                if (!accessLabel.isBlank()) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*km\\s+from\\s+centre", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(accessLabel);
                    if (m.find()) {
                        try { dist = Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
                    }
                }
                if (node.has("distance_to_cc")) {
                    dist = node.path("distance_to_cc").asDouble(dist);
                }
                hotel.setDistanceFromCenterKm(dist);

                // Free Cancellation
                if (accessLabel.toLowerCase().contains("free cancellation")) {
                    hotel.setFreeCancellationUntil("Free cancellation available");
                } else {
                    hotel.setFreeCancellationUntil("Flexible cancellation available");
                }

                // Local Pick — mark boutique/heritage Sri Lankan properties
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