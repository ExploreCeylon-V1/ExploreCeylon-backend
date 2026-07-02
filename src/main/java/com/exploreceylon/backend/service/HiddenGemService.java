package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.gem.CreateGemRequest;
import com.exploreceylon.backend.dto.gem.GemResponse;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.HiddenGemRepository;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HiddenGemService {

    private final HiddenGemRepository gemRepository;
    private final UserRepository      userRepository;

    // ── Get current user ───────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    // ── Get All Approved Gems ──────────────────────────────
    public List<GemResponse> getAllGems(
            HiddenGem.GemCategory category,
            String district) {

        List<HiddenGem> gems;

        if (category != null && district != null) {
            gems = gemRepository
                    .findByCategoryAndDistrictIgnoreCaseAndApprovedTrue(
                            category, district);
        } else if (category != null) {
            gems = gemRepository
                    .findByCategoryAndApprovedTrueOrderByRatingDesc(
                            category);
        } else if (district != null) {
            gems = gemRepository
                    .findByDistrictIgnoreCaseAndApprovedTrueOrderByRatingDesc(
                            district);
        } else {
            gems = gemRepository.findByApprovedTrueOrderByRatingDesc();
        }

        return gems.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Nearby (distance-sorted, for AI trip planning) ─────
    public List<GemResponse> findNearby(double lat, double lng, int limit) {
        return gemRepository.findByApprovedTrueOrderByRatingDesc()
                .stream()
                .filter(g -> g.getLatitude() != null && g.getLongitude() != null)
                .sorted(Comparator.comparingDouble(g -> GeoUtils.distanceKm(
                        lat, lng, g.getLatitude(), g.getLongitude())))
                .limit(limit)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Gem By ID ──────────────────────────────────────
    public GemResponse getGemById(Long id) {
        HiddenGem gem = gemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Gem not found: " + id));
        return toResponse(gem);
    }

    // ── Search Gems ────────────────────────────────────────
    public List<GemResponse> searchGems(String keyword) {
        log.info("Searching gems: {}", keyword);
        return gemRepository.searchByKeyword(keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Submit Gem (Community) ─────────────────────────────
    public GemResponse submitGem(CreateGemRequest req) {
        User user = getCurrentUser();
        log.info("Gem submitted by: {}",
                user != null ? user.getEmail() : "anonymous");

        HiddenGem gem = HiddenGem.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .district(req.getDistrict())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .howToGetThere(req.getHowToGetThere())
                .bestTime(req.getBestTime())
                .tips(req.getTips())
                .imageUrls(req.getImageUrls() != null
                        ? req.getImageUrls() : List.of())
                .submittedBy(user)
                .approved(false) // pending admin approval
                .build();

        return toResponse(gemRepository.save(gem));
    }

    // ── Admin — Add Approved Gem ───────────────────────────
    public GemResponse addGem(CreateGemRequest req) {
        log.info("Admin adding gem: {}", req.getTitle());

        HiddenGem gem = HiddenGem.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .district(req.getDistrict())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .howToGetThere(req.getHowToGetThere())
                .bestTime(req.getBestTime())
                .tips(req.getTips())
                .imageUrls(req.getImageUrls() != null
                        ? req.getImageUrls() : List.of())
                .approved(true) // admin adds → auto approved
                .build();

        return toResponse(gemRepository.save(gem));
    }

    // ── Admin — Approve Gem ────────────────────────────────
    public GemResponse approveGem(Long id) {
        HiddenGem gem = gemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Gem not found: " + id));
        gem.setApproved(true);
        log.info("Gem approved: {}", gem.getTitle());
        return toResponse(gemRepository.save(gem));
    }

    // ── Admin — Get Pending Gems ───────────────────────────
    public List<GemResponse> getPendingGems() {
        return gemRepository.findByApprovedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Admin — Update Gem ─────────────────────────────────
    public GemResponse updateGem(Long id, CreateGemRequest req) {
        HiddenGem gem = gemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Gem not found: " + id));

        if (req.getTitle()        != null) gem.setTitle(req.getTitle());
        if (req.getDescription()  != null) gem.setDescription(req.getDescription());
        if (req.getCategory()     != null) gem.setCategory(req.getCategory());
        if (req.getDistrict()     != null) gem.setDistrict(req.getDistrict());
        if (req.getLatitude()     != null) gem.setLatitude(req.getLatitude());
        if (req.getLongitude()    != null) gem.setLongitude(req.getLongitude());
        if (req.getHowToGetThere()!= null) gem.setHowToGetThere(req.getHowToGetThere());
        if (req.getBestTime()     != null) gem.setBestTime(req.getBestTime());
        if (req.getTips()         != null) gem.setTips(req.getTips());
        if (req.getImageUrls()    != null) gem.setImageUrls(req.getImageUrls());

        return toResponse(gemRepository.save(gem));
    }

    // ── Admin — Delete Gem ─────────────────────────────────
    public void deleteGem(Long id) {
        gemRepository.deleteById(id);
        log.info("Gem deleted: {}", id);
    }

    // ── MAPPER ─────────────────────────────────────────────
    private GemResponse toResponse(HiddenGem g) {
        GemResponse res = new GemResponse();
        res.setId(g.getId());
        res.setTitle(g.getTitle());
        res.setDescription(g.getDescription());
        res.setCategory(g.getCategory());
        res.setDistrict(g.getDistrict());
        res.setLatitude(g.getLatitude());
        res.setLongitude(g.getLongitude());
        res.setHowToGetThere(g.getHowToGetThere());
        res.setBestTime(g.getBestTime());
        res.setTips(g.getTips());
        res.setImageUrls(g.getImageUrls());
        res.setApproved(g.getApproved());
        res.setRating(g.getRating());
        res.setReviewCount(g.getReviewCount());
        res.setCreatedAt(g.getCreatedAt());
        return res;
    }
}