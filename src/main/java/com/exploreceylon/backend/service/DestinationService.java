package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.destination.CreateDestinationRequest;
import com.exploreceylon.backend.dto.destination.DestinationResponse;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DestinationService {

    private final DestinationRepository destinationRepository;

    // ── Get All Destinations ───────────────────────────────
    public List<DestinationResponse> getAllDestinations(
            Destination.DestinationCategory category,
            String province,
            String month,
            boolean includeAll) {

        List<Destination> destinations;

        // 1. PUBLIC WEBSITE: සාමාන්‍ය වෙබ්සයිට් එකට (Active කරලා තියෙන ඒවා විතරයි පෙන්වන්නේ)
        if (!includeAll) {
            if (category != null && province != null) {
                destinations = destinationRepository
                        .findByCategoryAndProvinceIgnoreCaseAndActiveTrue(category, province);
            } else if (category != null) {
                destinations = destinationRepository
                        .findByCategoryAndActiveTrueOrderByRatingDesc(category);
            } else if (province != null) {
                destinations = destinationRepository
                        .findByProvinceIgnoreCaseAndActiveTrueOrderByRatingDesc(province);
            } else if (month != null) {
                // මෙහිදී අදාළ මාසයේ දත්ත ගන්නවා
                destinations = destinationRepository.findByBestMonth(month);
            } else {
                destinations = destinationRepository
                        .findByActiveTrueOrderByRatingDesc();
            }
        } 
        // 2. ADMIN PANEL: ඇඩ්මින් පැනල් එකට (Active, Inactive ඔක්කොම පෙන්වනවා)
        else {
            // Admin සඳහා Database එකේ තියෙන 12 ම (සියල්ලම) ගන්නවා
            destinations = destinationRepository.findAll();
            
            // Admin Panel එකෙනුත් Dropdown හරහා Category / Province ෆිල්ටර් කළොත් ඒ ටික වෙන් කරනවා
            if (category != null) {
                destinations = destinations.stream()
                        .filter(d -> d.getCategory() == category)
                        .collect(Collectors.toList());
            }
            if (province != null) {
                destinations = destinations.stream()
                        .filter(d -> d.getProvince() != null && d.getProvince().equalsIgnoreCase(province))
                        .collect(Collectors.toList());
            }
        }

        // අවසානයේ Response එකට Map කරලා යවනවා
        return destinations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Featured Destinations ──────────────────────────
    public List<DestinationResponse> getFeatured() {
        return destinationRepository
                .findByFeaturedTrueAndActiveTrueOrderByRatingDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Destination By ID ──────────────────────────────
    public DestinationResponse getById(Long id) {
        Destination dest = destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Destination not found: " + id));
        return toResponse(dest);
    }

    // ── Search ─────────────────────────────────────────────
    public List<DestinationResponse> search(String keyword) {
        log.info("Searching destinations: {}", keyword);
        return destinationRepository
                .searchDestinations(keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Admin — Create ─────────────────────────────────────
    public DestinationResponse create(CreateDestinationRequest req) {
        log.info("Creating destination: {}", req.getName());
        Destination dest = Destination.builder()
                .name(req.getName())
                .district(req.getDistrict())
                .province(req.getProvince())
                .description(req.getDescription())
                .shortDescription(req.getShortDescription())
                .category(req.getCategory())
                .bestMonths(req.getBestMonths())
                .activities(req.getActivities())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .coverImageUrl(req.getCoverImageUrl())
                .imageUrls(req.getImageUrls() != null
                        ? req.getImageUrls() : List.of())
                .travelTimeFrom(req.getTravelTimeFrom())
                .entryFee(req.getEntryFee())
                .openingHours(req.getOpeningHours())
                .featured(req.getFeatured() != null
                        ? req.getFeatured() : false)
                .unescoStatus(req.getUnescoStatus())
                .nearbyGems(req.getNearbyGems())
                .active(req.getActive() != null ? req.getActive() : true)
                .build();
        return toResponse(destinationRepository.save(dest));
    }

    // ── Admin — Update ─────────────────────────────────────
    public DestinationResponse update(Long id,
                                       CreateDestinationRequest req) {
        Destination dest = destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Destination not found: " + id));

        if (req.getName()            != null) dest.setName(req.getName());
        if (req.getDistrict()        != null) dest.setDistrict(req.getDistrict());
        if (req.getProvince()        != null) dest.setProvince(req.getProvince());
        if (req.getDescription()     != null) dest.setDescription(req.getDescription());
        if (req.getShortDescription()!= null) dest.setShortDescription(req.getShortDescription());
        if (req.getCategory()        != null) dest.setCategory(req.getCategory());
        if (req.getBestMonths()      != null) dest.setBestMonths(req.getBestMonths());
        if (req.getActivities()      != null) dest.setActivities(req.getActivities());
        if (req.getLatitude()        != null) dest.setLatitude(req.getLatitude());
        if (req.getLongitude()       != null) dest.setLongitude(req.getLongitude());
        if (req.getCoverImageUrl()   != null) dest.setCoverImageUrl(req.getCoverImageUrl());
        if (req.getTravelTimeFrom()  != null) dest.setTravelTimeFrom(req.getTravelTimeFrom());
        if (req.getEntryFee()        != null) dest.setEntryFee(req.getEntryFee());
        if (req.getOpeningHours()    != null) dest.setOpeningHours(req.getOpeningHours());
        if (req.getFeatured()        != null) dest.setFeatured(req.getFeatured());
        if (req.getUnescoStatus()    != null) dest.setUnescoStatus(req.getUnescoStatus());
        if (req.getNearbyGems()      != null) dest.setNearbyGems(req.getNearbyGems());
        if (req.getActive()          != null) dest.setActive(req.getActive());

        return toResponse(destinationRepository.save(dest));
    }

    // ── Admin — Toggle Featured ────────────────────────────
    public DestinationResponse toggleFeatured(Long id,
                                               Boolean featured) {
        Destination dest = destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Destination not found: " + id));
        dest.setFeatured(featured);
        return toResponse(destinationRepository.save(dest));
    }

    // ── Admin — Delete ─────────────────────────────────────
    public void delete(Long id) {
        Destination dest = destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Destination not found: " + id));
        destinationRepository.delete(dest);
        log.info("Destination permanently deleted: {}", id);
    }

    // ── MAPPER ─────────────────────────────────────────────
    private DestinationResponse toResponse(Destination d) {
        DestinationResponse res = new DestinationResponse();
        res.setId(d.getId());
        res.setName(d.getName());
        res.setDistrict(d.getDistrict());
        res.setProvince(d.getProvince());
        res.setDescription(d.getDescription());
        res.setShortDescription(d.getShortDescription());
        res.setCategory(d.getCategory());
        res.setBestMonths(d.getBestMonths());
        res.setActivities(d.getActivities());
        res.setLatitude(d.getLatitude());
        res.setLongitude(d.getLongitude());
        res.setCoverImageUrl(d.getCoverImageUrl());
        res.setImageUrls(d.getImageUrls());
        res.setTravelTimeFrom(d.getTravelTimeFrom());
        res.setEntryFee(d.getEntryFee());
        res.setOpeningHours(d.getOpeningHours());
        res.setFeatured(d.getFeatured());
        res.setActive(d.getActive()); 
        res.setRating(d.getRating());
        res.setReviewCount(d.getReviewCount());
        res.setUnescoStatus(d.getUnescoStatus());
        res.setNearbyGems(d.getNearbyGems());
        return res;
    }

    // ── Toggle Active Status ────────────────────────────────────
    public DestinationResponse toggleActive(Long id, Boolean active) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Destination not found"));
        
        destination.setActive(active);
        Destination saved = destinationRepository.save(destination);
        
        return toResponse(saved); 
    }
}