package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.hotel.HotelResult;
import com.exploreceylon.backend.dto.hotel.HotelSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.stream.Collectors;

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

    // ═══════════════════════════════════════════════════════════
    // CURATED SRI LANKA HOTEL FALLBACK DATABASE
    // ═══════════════════════════════════════════════════════════
    private static final List<HotelResult> CURATED_SRI_LANKA_HOTELS = createCuratedHotelDataset();

    public HotelApiService(@Qualifier("hotelWebClient") WebClient hotelWebClient) {
        this.hotelWebClient = hotelWebClient;
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 1 — Location Name → dest_id
    // ═══════════════════════════════════════════════════════════
    public Mono<String> getDestinationId(String locationName) {
        log.info("Getting destination ID for: {}", locationName);

        if (rapidApiKey == null || rapidApiKey.isBlank() || rapidApiKey.contains("mock_rapidapi_key")) {
            log.info("Using mock/fallback key for destination lookup: {}", locationName);
            return Mono.just("-2211532");
        }

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
                    log.warn("Location API error: {} - {}. Using Colombo default", e.getStatusCode(), e.getMessage());
                    return Mono.just("-2211532");
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("Unexpected location error: {}. Using Colombo default", e.getMessage());
                    return Mono.just("-2211532");
                });
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 2 — Search Hotels (Public Method)
    // ═══════════════════════════════════════════════════════════
    public Mono<List<HotelResult>> searchHotels(HotelSearchRequest request) {
        log.info("Searching hotels for location: {}", request.getLocation());

        if (rapidApiKey == null || rapidApiKey.isBlank() || rapidApiKey.contains("mock_rapidapi_key")) {
            log.info("Mock or unconfigured RapidAPI key detected. Serving curated Sri Lanka hotels.");
            return Mono.just(getFallbackHotels(request.getLocation(), request.getCurrency()));
        }

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
                        .queryParam("adults_number", String.valueOf(request.getAdults()))
                        .queryParam("room_number", String.valueOf(request.getRooms()))
                        .queryParam("currency",
                                request.getCurrency() != null && !request.getCurrency().isBlank()
                                        ? request.getCurrency() : "USD")
                        .queryParam("filter_by_currency",
                                request.getCurrency() != null && !request.getCurrency().isBlank()
                                        ? request.getCurrency() : "USD")
                        .queryParam("locale", "en-gb")
                        .queryParam("order_by", "popularity")
                        .queryParam("units", "metric")
                        .queryParam("include_adjacency", "true")
                        .build())
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", rapidApiHost)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> parseHotelResults(json, request.getCurrency()))
                .flatMap(results -> {
                    if (results.isEmpty()) {
                        log.warn("RapidAPI returned 0 results for destId: {}. Serving curated Sri Lanka hotels.", destId);
                        return Mono.just(getFallbackHotels(request.getLocation(), request.getCurrency()));
                    }
                    return Mono.just(results);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Hotel search API error ({}: {}). Serving curated Sri Lanka hotels.",
                            e.getStatusCode(), e.getMessage());
                    return Mono.just(getFallbackHotels(request.getLocation(), request.getCurrency()));
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("Unexpected hotel search error ({}). Serving curated Sri Lanka hotels.", e.getMessage());
                    return Mono.just(getFallbackHotels(request.getLocation(), request.getCurrency()));
                });
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 4 — Get Hotel Details by ID
    // ═══════════════════════════════════════════════════════════
    public Mono<JsonNode> getHotelDetails(String hotelId) {
        log.info("Getting hotel details for ID: {}", hotelId);

        // Check fallback dataset first if hotelId starts with sl-hotel-
        if (hotelId != null && hotelId.startsWith("sl-hotel-")) {
            HotelResult match = CURATED_SRI_LANKA_HOTELS.stream()
                    .filter(h -> h.getHotelId().equalsIgnoreCase(hotelId))
                    .findFirst()
                    .orElse(CURATED_SRI_LANKA_HOTELS.get(0));
            return Mono.just(createSyntheticHotelDetailsNode(match));
        }

        if (rapidApiKey == null || rapidApiKey.isBlank() || rapidApiKey.contains("mock_rapidapi_key")) {
            HotelResult match = CURATED_SRI_LANKA_HOTELS.get(0);
            return Mono.just(createSyntheticHotelDetailsNode(match));
        }

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
                    log.warn("Hotel details API error ({}), falling back to curated details.", e.getStatusCode());
                    return Mono.just(createSyntheticHotelDetailsNode(CURATED_SRI_LANKA_HOTELS.get(0)));
                });
    }

    // ═══════════════════════════════════════════════════════════
    // DYNAMIC FALLBACK FILTERING
    // ═══════════════════════════════════════════════════════════
    public List<HotelResult> getFallbackHotels(String locationQuery, String currency) {
        String query = locationQuery != null ? locationQuery.toLowerCase().trim() : "";

        List<HotelResult> filtered = CURATED_SRI_LANKA_HOTELS.stream()
                .filter(hotel -> {
                    if (query.isEmpty() || query.contains("sri lanka") || query.contains("all")) {
                        return true;
                    }
                    String addr = hotel.getAddress().toLowerCase();
                    String name = hotel.getName().toLowerCase();
                    return addr.contains(query) || name.contains(query) || query.contains(addr.split(",")[0].trim());
                })
                .collect(Collectors.toList());

        // If no direct city match, return top curated hotels across Sri Lanka
        if (filtered.isEmpty()) {
            filtered = new ArrayList<>(CURATED_SRI_LANKA_HOTELS);
        }

        log.info("Serving {} curated Sri Lanka hotels for location query: '{}'", filtered.size(), locationQuery);
        return filtered;
    }

    // ═══════════════════════════════════════════════════════════
    // PARSE — API Response → HotelResult List
    // ═══════════════════════════════════════════════════════════
    private List<HotelResult> parseHotelResults(JsonNode response, String requestedCurrency) {
        List<HotelResult> hotels = new ArrayList<>();

        if (response == null) return hotels;

        JsonNode results = response.path("result");
        if (results.isMissingNode() || !results.isArray()) {
            results = response.path("data");
        }

        if (results.isMissingNode() || !results.isArray()) {
            log.warn("No valid result array found in hotel API response");
            return hotels;
        }

        for (JsonNode node : results) {
            try {
                HotelResult hotel = new HotelResult();

                hotel.setHotelId(node.path("hotel_id").asText(node.path("id").asText("sl-hotel-x")));
                hotel.setName(node.path("hotel_name").asText(node.path("name").asText("Sri Lanka Boutique Hotel")));

                String address = node.path("address").asText("");
                String city = node.path("city").asText("");
                hotel.setAddress(address.isEmpty() ? city : address + ", " + city);

                hotel.setReviewScore(node.path("review_score").asDouble(node.path("rating").asDouble(8.8)));
                hotel.setReviewScoreWord(node.path("review_score_word").asText("Fabulous"));
                hotel.setReviewsCount(node.path("review_nr").asInt(node.path("reviews_count").asInt(142)));
                hotel.setPricePerNight(node.path("min_total_price").asDouble(node.path("price").asDouble(85.0)));
                hotel.setCurrency(node.path("currency_code").asText(
                        requestedCurrency != null && !requestedCurrency.isBlank() ? requestedCurrency : "USD"));
                hotel.setStars(node.path("class").asInt(4));
                hotel.setPhotoUrl(node.path("main_photo_url").asText(node.path("photo_url").asText("")));

                String accommodationType = node.path("accommodation_type_name").asText("Hotel");
                hotel.setPropertyType(accommodationType);

                List<String> amenitiesList = new ArrayList<>();
                JsonNode facilitiesNode = node.path("hotel_facilities");
                if (facilitiesNode.isTextual() && !facilitiesNode.asText().isBlank()) {
                    amenitiesList.addAll(Arrays.asList(facilitiesNode.asText().split("\\s*,\\s*")));
                } else if (facilitiesNode.isArray()) {
                    facilitiesNode.forEach(f -> {
                        String name = f.isTextual() ? f.asText() : f.path("name").asText("");
                        if (!name.isBlank()) amenitiesList.add(name);
                    });
                }
                if (node.path("has_swimming_pool").asInt(0) == 1) amenitiesList.add("Swimming Pool");
                if (node.path("is_free_cancellable").asInt(0) == 1) amenitiesList.add("Free Cancellation");
                if (amenitiesList.isEmpty()) {
                    amenitiesList.addAll(List.of("Free WiFi", "Air Conditioning", "Breakfast Included", "Swimming Pool"));
                }
                hotel.setAmenities(amenitiesList);

                if (node.has("distance_to_cc")) {
                    hotel.setDistanceFromCenterKm(node.path("distance_to_cc").asDouble());
                } else {
                    hotel.setDistanceFromCenterKm(1.2);
                }

                if (node.path("is_free_cancellable").asInt(0) == 1
                        && !node.path("free_cancellation_until").asText("").isBlank()) {
                    hotel.setFreeCancellationUntil(node.path("free_cancellation_until").asText());
                } else {
                    hotel.setFreeCancellationUntil("Flexible 24h cancellation");
                }

                String hotelName = hotel.getName().toLowerCase();
                boolean isLocal = LOCAL_KEYWORDS.stream().anyMatch(hotelName::contains);
                hotel.setLocalPick(isLocal);

                hotels.add(hotel);

            } catch (Exception e) {
                log.warn("Failed to parse hotel node: {}", e.getMessage());
            }
        }

        log.info("Parsed {} hotels successfully from RapidAPI response", hotels.size());
        return hotels;
    }

    private JsonNode createSyntheticHotelDetailsNode(HotelResult hotel) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("hotel_id", hotel.getHotelId());
        root.put("name", hotel.getName());
        root.put("address", hotel.getAddress());
        root.put("rating", hotel.getReviewScore());
        root.put("price", hotel.getPricePerNight());
        root.put("currency", hotel.getCurrency());
        root.put("photo_url", hotel.getPhotoUrl());
        return root;
    }

    // ═══════════════════════════════════════════════════════════
    // CURATED SRI LANKA HOTELS INITIALIZER
    // ═══════════════════════════════════════════════════════════
    private static List<HotelResult> createCuratedHotelDataset() {
        List<HotelResult> list = new ArrayList<>();

        // 1. Cinnamon Grand Colombo
        HotelResult h1 = new HotelResult();
        h1.setHotelId("sl-hotel-101");
        h1.setName("Cinnamon Grand Colombo");
        h1.setAddress("77 Galle Road, Colombo 03, Colombo");
        h1.setReviewScore(9.2);
        h1.setReviewScoreWord("Superb");
        h1.setReviewsCount(1480);
        h1.setPricePerNight(135.0);
        h1.setCurrency("USD");
        h1.setStars(5);
        h1.setPhotoUrl("https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800&q=80");
        h1.setLocalPick(true);
        h1.setPropertyType("Hotel");
        h1.setAmenities(List.of("Swimming Pool", "Spa & Wellness", "Free WiFi", "Fitness Center", "Airport Shuttle", "Bar"));
        h1.setDistanceFromCenterKm(0.8);
        h1.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h1);

        // 2. Galle Face Hotel
        HotelResult h2 = new HotelResult();
        h2.setHotelId("sl-hotel-102");
        h2.setName("Galle Face Hotel");
        h2.setAddress("2 Kollupitiya Road, Colombo 03, Colombo");
        h2.setReviewScore(9.0);
        h2.setReviewScoreWord("Superb");
        h2.setReviewsCount(2120);
        h2.setPricePerNight(160.0);
        h2.setCurrency("USD");
        h2.setStars(5);
        h2.setPhotoUrl("https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80");
        h2.setLocalPick(true);
        h2.setPropertyType("Hotel");
        h2.setAmenities(List.of("Ocean View", "Saltwater Pool", "Heritage Museum", "Free WiFi", "Fine Dining"));
        h2.setDistanceFromCenterKm(1.1);
        h2.setFreeCancellationUntil("Free cancellation up to 48h before check-in");
        list.add(h2);

        // 3. Marino Beach Colombo
        HotelResult h3 = new HotelResult();
        h3.setHotelId("sl-hotel-103");
        h3.setName("Marino Beach Colombo");
        h3.setAddress("590 Galle Road, Colombo 03, Colombo");
        h3.setReviewScore(8.9);
        h3.setReviewScoreWord("Fabulous");
        h3.setReviewsCount(3400);
        h3.setPricePerNight(95.0);
        h3.setCurrency("USD");
        h3.setStars(4);
        h3.setPhotoUrl("https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=800&q=80");
        h3.setLocalPick(false);
        h3.setPropertyType("Hotel");
        h3.setAmenities(List.of("Rooftop Infinity Pool", "Sea View", "Free WiFi", "Shopping Mall Access", "Spa"));
        h3.setDistanceFromCenterKm(2.4);
        h3.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h3);

        // 4. The Grand Kandyan
        HotelResult h4 = new HotelResult();
        h4.setHotelId("sl-hotel-104");
        h4.setName("The Grand Kandyan");
        h4.setAddress("899 Lady Gordon's Drive, Kandy");
        h4.setReviewScore(9.1);
        h4.setReviewScoreWord("Superb");
        h4.setReviewsCount(980);
        h4.setPricePerNight(120.0);
        h4.setCurrency("USD");
        h4.setStars(5);
        h4.setPhotoUrl("https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=800&q=80");
        h4.setLocalPick(true);
        h4.setPropertyType("Resort");
        h4.setAmenities(List.of("Mountain View", "Rooftop Pool", "Free WiFi", "Spa", "Restaurant"));
        h4.setDistanceFromCenterKm(1.5);
        h4.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h4);

        // 5. Earl's Regency Hotel
        HotelResult h5 = new HotelResult();
        h5.setHotelId("sl-hotel-105");
        h5.setName("Earl's Regency Hotel");
        h5.setAddress("Tennekumbura, Kandy");
        h5.setReviewScore(8.8);
        h5.setReviewScoreWord("Fabulous");
        h5.setReviewsCount(1150);
        h5.setPricePerNight(110.0);
        h5.setCurrency("USD");
        h5.setStars(5);
        h5.setPhotoUrl("https://images.unsplash.com/photo-1564501049412-61c2a3083791?auto=format&fit=crop&w=800&q=80");
        h5.setLocalPick(true);
        h5.setPropertyType("Resort");
        h5.setAmenities(List.of("River View", "Outdoor Pool", "Ayurvedic Spa", "Free WiFi", "Tennis Court"));
        h5.setDistanceFromCenterKm(3.8);
        h5.setFreeCancellationUntil("Free cancellation up to 48h before check-in");
        list.add(h5);

        // 6. Amangalla
        HotelResult h6 = new HotelResult();
        h6.setHotelId("sl-hotel-106");
        h6.setName("Amangalla Resort");
        h6.setAddress("10 Church Street, Galle Fort, Galle");
        h6.setReviewScore(9.6);
        h6.setReviewScoreWord("Exceptional");
        h6.setReviewsCount(420);
        h6.setPricePerNight(480.0);
        h6.setCurrency("USD");
        h6.setStars(5);
        h6.setPhotoUrl("https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?auto=format&fit=crop&w=800&q=80");
        h6.setLocalPick(true);
        h6.setPropertyType("Boutique Villa");
        h6.setAmenities(List.of("Historic Heritage", "Hydrotherapy Spa", "Swimming Pool", "Butler Service", "Free WiFi"));
        h6.setDistanceFromCenterKm(0.2);
        h6.setFreeCancellationUntil("Free cancellation up to 7 days before check-in");
        list.add(h6);

        // 7. Jetwing Lighthouse
        HotelResult h7 = new HotelResult();
        h7.setHotelId("sl-hotel-107");
        h7.setName("Jetwing Lighthouse");
        h7.setAddress("Dadella, Galle");
        h7.setReviewScore(9.2);
        h7.setReviewScoreWord("Superb");
        h7.setReviewsCount(1650);
        h7.setPricePerNight(175.0);
        h7.setCurrency("USD");
        h7.setStars(5);
        h7.setPhotoUrl("https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?auto=format&fit=crop&w=800&q=80");
        h7.setLocalPick(true);
        h7.setPropertyType("Hotel");
        h7.setAmenities(List.of("Oceanfront", "2 Swimming Pools", "Tennis Court", "Luxury Spa", "Free WiFi"));
        h7.setDistanceFromCenterKm(2.8);
        h7.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h7);

        // 8. 98 Acres Resort & Spa
        HotelResult h8 = new HotelResult();
        h8.setHotelId("sl-hotel-108");
        h8.setName("98 Acres Resort & Spa");
        h8.setAddress("Green Tea Estate, Passara Road, Ella");
        h8.setReviewScore(9.5);
        h8.setReviewScoreWord("Exceptional");
        h8.setReviewsCount(2100);
        h8.setPricePerNight(240.0);
        h8.setCurrency("USD");
        h8.setStars(5);
        h8.setPhotoUrl("https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=800&q=80");
        h8.setLocalPick(true);
        h8.setPropertyType("Eco Lodge");
        h8.setAmenities(List.of("Tea Estate View", "Helipad", "Infinity Pool", "Spa", "Free WiFi", "Hiking Trails"));
        h8.setDistanceFromCenterKm(1.8);
        h8.setFreeCancellationUntil("Free cancellation up to 48h before check-in");
        list.add(h8);

        // 9. Heritance Kandalama
        HotelResult h9 = new HotelResult();
        h9.setHotelId("sl-hotel-109");
        h9.setName("Heritance Kandalama");
        h9.setAddress("P.O Box 11, Dambulla, Sigiriya");
        h9.setReviewScore(9.3);
        h9.setReviewScoreWord("Superb");
        h9.setReviewsCount(2800);
        h9.setPricePerNight(185.0);
        h9.setCurrency("USD");
        h9.setStars(5);
        h9.setPhotoUrl("https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=800&q=80");
        h9.setLocalPick(true);
        h9.setPropertyType("Eco Resort");
        h9.setAmenities(List.of("Lake View", "3 Infinity Pools", "Six Senses Spa", "Sigiriya Rock View", "Free WiFi"));
        h9.setDistanceFromCenterKm(9.5);
        h9.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h9);

        // 10. Water Garden Sigiriya
        HotelResult h10 = new HotelResult();
        h10.setHotelId("sl-hotel-110");
        h10.setName("Water Garden Sigiriya");
        h10.setAddress("Indigaswewa, Sigiriya");
        h10.setReviewScore(9.4);
        h10.setReviewScoreWord("Exceptional");
        h10.setReviewsCount(650);
        h10.setPricePerNight(310.0);
        h10.setCurrency("USD");
        h10.setStars(5);
        h10.setPhotoUrl("https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=800&q=80");
        h10.setLocalPick(true);
        h10.setPropertyType("Boutique Villa");
        h10.setAmenities(List.of("Private Pool Villa", "Sigiriya Fortress View", "Spa", "Free WiFi", "Organic Dining"));
        h10.setDistanceFromCenterKm(5.2);
        h10.setFreeCancellationUntil("Free cancellation up to 48h before check-in");
        list.add(h10);

        // 11. The Grand Hotel Nuwara Eliya
        HotelResult h11 = new HotelResult();
        h11.setHotelId("sl-hotel-111");
        h11.setName("The Grand Hotel Nuwara Eliya");
        h11.setAddress("Grand Hotel Road, Nuwara Eliya");
        h11.setReviewScore(9.0);
        h11.setReviewScoreWord("Superb");
        h11.setReviewsCount(1950);
        h11.setPricePerNight(145.0);
        h11.setCurrency("USD");
        h11.setStars(4);
        h11.setPhotoUrl("https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800&q=80");
        h11.setLocalPick(true);
        h11.setPropertyType("Heritage Hotel");
        h11.setAmenities(List.of("Colonial Tea Lounge", "Heated Pool", "Award Winning Gardens", "Free WiFi", "Billiards"));
        h11.setDistanceFromCenterKm(0.5);
        h11.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h11);

        // 12. Heritance Tea Factory
        HotelResult h12 = new HotelResult();
        h12.setHotelId("sl-hotel-112");
        h12.setName("Heritance Tea Factory");
        h12.setAddress("Kandapola, Nuwara Eliya");
        h12.setReviewScore(9.2);
        h12.setReviewScoreWord("Superb");
        h12.setReviewsCount(1230);
        h12.setPricePerNight(170.0);
        h12.setCurrency("USD");
        h12.setStars(5);
        h12.setPhotoUrl("https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=800&q=80");
        h12.setLocalPick(true);
        h12.setPropertyType("Resort");
        h12.setAmenities(List.of("Tea Plucking Experience", "Mountain View", "Organic Spa", "Free WiFi", "Fine Dining"));
        h12.setDistanceFromCenterKm(12.0);
        h12.setFreeCancellationUntil("Free cancellation up to 48h before check-in");
        list.add(h12);

        // 13. Taj Bentota Resort & Spa
        HotelResult h13 = new HotelResult();
        h13.setHotelId("sl-hotel-113");
        h13.setName("Taj Bentota Resort & Spa");
        h13.setAddress("National Holiday Resort, Bentota");
        h13.setReviewScore(9.1);
        h13.setReviewScoreWord("Superb");
        h13.setReviewsCount(2400);
        h13.setPricePerNight(190.0);
        h13.setCurrency("USD");
        h13.setStars(5);
        h13.setPhotoUrl("https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=800&q=80");
        h13.setLocalPick(false);
        h13.setPropertyType("Resort");
        h13.setAmenities(List.of("Private Beach", "Jiva Spa", "Outdoor Pool", "Free WiFi", "Water Sports"));
        h13.setDistanceFromCenterKm(1.0);
        h13.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h13);

        // 14. Cinnamon Wild Yala
        HotelResult h14 = new HotelResult();
        h14.setHotelId("sl-hotel-114");
        h14.setName("Cinnamon Wild Yala");
        h14.setAddress("Palatupana, Kirinda, Yala");
        h14.setReviewScore(8.9);
        h14.setReviewScoreWord("Fabulous");
        h14.setReviewsCount(1580);
        h14.setPricePerNight(165.0);
        h14.setCurrency("USD");
        h14.setStars(4);
        h14.setPhotoUrl("https://images.unsplash.com/photo-1564501049412-61c2a3083791?auto=format&fit=crop&w=800&q=80");
        h14.setLocalPick(true);
        h14.setPropertyType("Safari Lodge");
        h14.setAmenities(List.of("Wildlife Viewing Deck", "Pool", "Game Drives", "Free WiFi", "Beachfront"));
        h14.setDistanceFromCenterKm(4.5);
        h14.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h14);

        // 15. Weligama Bay Marriott Resort & Spa
        HotelResult h15 = new HotelResult();
        h15.setHotelId("sl-hotel-115");
        h15.setName("Weligama Bay Marriott Resort & Spa");
        h15.setAddress("700 Matara Road, Pelana, Weligama, Mirissa");
        h15.setReviewScore(9.3);
        h15.setReviewScoreWord("Superb");
        h15.setReviewsCount(2900);
        h15.setPricePerNight(210.0);
        h15.setCurrency("USD");
        h15.setStars(5);
        h15.setPhotoUrl("https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80");
        h15.setLocalPick(false);
        h15.setPropertyType("Hotel");
        h15.setAmenities(List.of("Ocean View Balconies", "3 Outdoor Pools", "Quan Spa", "Kids Club", "Free WiFi"));
        h15.setDistanceFromCenterKm(1.5);
        h15.setFreeCancellationUntil("Free cancellation up to 24h before check-in");
        list.add(h15);

        return list;
    }
}