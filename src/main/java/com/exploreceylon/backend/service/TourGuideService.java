package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.guide.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TourGuideService {

    private final TourGuideRepository    guideRepository;
    private final GuideBookingRepository bookingRepository;
    private final GuideReviewRepository  reviewRepository;
    private final UserRepository         userRepository;
    private final TripRepository         tripRepository;
    private final BudgetService          budgetService;

    // ── Current User ───────────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── Get All Guides ─────────────────────────────────────
    public List<GuideResponse> getAllGuides(
            String district, String language,
            String specialty, Double maxPrice) {
        return getAllGuides(district, language, specialty, maxPrice, null, null);
    }

    // Overload — when startDate/endDate are both given, guides with an active
    // booking overlapping that range are excluded from the results.
    public List<GuideResponse> getAllGuides(
            String district, String language,
            String specialty, Double maxPrice,
            LocalDate startDate, LocalDate endDate) {

        List<TourGuide> guides;

        if (language != null) {
            guides = guideRepository.findByLanguage(language);
        } else if (specialty != null) {
            guides = guideRepository.findBySpecialty(specialty);
        } else if (district != null && maxPrice != null) {
            guides = guideRepository
                    .findByDistrictIgnoreCaseAndPricePerDayLessThanEqualAndVerifiedTrue(
                            district, maxPrice);
        } else if (district != null) {
            guides = guideRepository
                    .findByDistrictIgnoreCaseAndVerifiedTrueAndAvailableTrue(
                            district);
        } else if (maxPrice != null) {
            guides = guideRepository
                    .findByPricePerDayLessThanEqualAndVerifiedTrue(maxPrice);
        } else {
            guides = guideRepository
                    .findByVerifiedTrueAndAvailableTrueOrderByRatingDesc();
        }

        if (startDate != null && endDate != null) {
            List<Long> bookedIds = bookingRepository.findBookedGuideIds(startDate, endDate);
            guides = guides.stream()
                    .filter(g -> !bookedIds.contains(g.getId()))
                    .collect(Collectors.toList());
        }

        return guides.stream()
                .map(this::toGuideResponse)
                .collect(Collectors.toList());
    }

    // ── Get Guide By ID ────────────────────────────────────
    public GuideResponse getGuideById(Long id) {
        return toGuideResponse(findGuide(id));
    }

    // ── Search Guides ──────────────────────────────────────
    public List<GuideResponse> searchGuides(String keyword) {
        return guideRepository.searchGuides(keyword)
                .stream()
                .map(this::toGuideResponse)
                .collect(Collectors.toList());
    }

    // ── Admin — Add Guide ──────────────────────────────────
    public GuideResponse addGuide(CreateGuideRequest req) {
        log.info("Admin adding guide: {}", req.getFullName());
        TourGuide guide = TourGuide.builder()
                .fullName(req.getFullName())
                .bio(req.getBio())
                .photoUrl(req.getPhotoUrl())
                .languages(req.getLanguages())
                .specialties(req.getSpecialties())
                .district(req.getDistrict())
                .pricePerDay(req.getPricePerDay())
                .phone(req.getPhone())
                .whatsappNumber(req.getWhatsappNumber())
                .email(req.getEmail())
                .imageUrls(req.getImageUrls() != null
                        ? req.getImageUrls() : List.of())
                .verified(true)
                .available(true)
                .build();
        return toGuideResponse(guideRepository.save(guide));
    }

    // ── Admin — Update Guide ───────────────────────────────
    public GuideResponse updateGuide(Long id, CreateGuideRequest req) {
        TourGuide guide = findGuide(id);
        if (req.getFullName()   != null) guide.setFullName(req.getFullName());
        if (req.getBio()        != null) guide.setBio(req.getBio());
        if (req.getPhotoUrl()   != null) guide.setPhotoUrl(req.getPhotoUrl());
        if (req.getLanguages()  != null) guide.setLanguages(req.getLanguages());
        if (req.getSpecialties()!= null) guide.setSpecialties(req.getSpecialties());
        if (req.getDistrict()   != null) guide.setDistrict(req.getDistrict());
        if (req.getPricePerDay()!= null) guide.setPricePerDay(req.getPricePerDay());
        if (req.getPhone()      != null) guide.setPhone(req.getPhone());
        if (req.getWhatsappNumber() != null) guide.setWhatsappNumber(req.getWhatsappNumber());
        if (req.getEmail()      != null) guide.setEmail(req.getEmail());
        if (req.getImageUrls()  != null) guide.setImageUrls(req.getImageUrls());
        return toGuideResponse(guideRepository.save(guide));
    }

    // ── Admin — Toggle Availability ────────────────────────
    public GuideResponse toggleAvailability(Long id, Boolean available) {
        TourGuide guide = findGuide(id);
        guide.setAvailable(available);
        return toGuideResponse(guideRepository.save(guide));
    }

    // ── Admin — Delete Guide ───────────────────────────────
    public void deleteGuide(Long id) {
        guideRepository.deleteById(id);
        log.info("Guide deleted: {}", id);
    }

    // ── Book Guide ─────────────────────────────────────────
    public GuideBookingResponse bookGuide(BookGuideRequest req) {
        User user   = getCurrentUser();
        TourGuide guide = findGuide(req.getGuideId());

        // Calculate total cost
        long days = ChronoUnit.DAYS.between(
                req.getStartDate(), req.getEndDate()) + 1;
        double totalCost = guide.getPricePerDay() * days;
        double advanceAmount = Math.round(totalCost * 0.20 * 100.0) / 100.0;
        double balanceAmount = Math.round((totalCost - advanceAmount) * 100.0) / 100.0;

        // Get trip if provided
        Trip trip = null;
        if (req.getTripId() != null) {
            trip = tripRepository.findById(req.getTripId())
                    .orElse(null);
        }

        GuideBooking booking = GuideBooking.builder()
                .guide(guide)
                .user(user)
                .trip(trip)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .totalCost(totalCost)
                .advanceAmount(advanceAmount)
                .balanceAmount(balanceAmount)
                .notes(req.getNotes())
                .status(GuideBooking.BookingStatus.PENDING_PAYMENT)
                .build();

        GuideBooking saved = bookingRepository.save(booking);
        log.info("Guide booked: {} by {}", guide.getFullName(),
                user.getEmail());

        // Feed the trip's budget tracker (no-op if the trip has no budget)
        if (trip != null) {
            budgetService.autoAddFromBooking(
                    trip.getId(),
                    BudgetItem.ItemCategory.GUIDE,
                    guide.getFullName() + " (" + days
                            + (days == 1 ? " day)" : " days)"),
                    totalCost,
                    "GB-" + saved.getId(),
                    req.getStartDate());
        }
        return toBookingResponse(saved);
    }

    // ── Get My Bookings ────────────────────────────────────
    public List<GuideBookingResponse> getMyBookings() {
        User user = getCurrentUser();
        return bookingRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
    }

    // ── Booking Detail (with ownership check) ──────────────
    public GuideBookingResponse getBookingById(Long id) {
        User user = getCurrentUser();
        GuideBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + id));
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your booking");
        }
        return toBookingResponse(booking);
    }

    // ── Check Availability ─────────────────────────────────
    public boolean checkAvailability(Long guideId, LocalDate start, LocalDate end) {
        findGuide(guideId);
        return bookingRepository.countOverlappingBookings(guideId, start, end) == 0;
    }

    // ── Get Guide Bookings ─────────────────────────────────
    public List<GuideBookingResponse> getGuideBookingsByGuideId(Long guideId) {
        findGuide(guideId);
        return bookingRepository
                .findByGuideIdOrderByCreatedAtDesc(guideId)
                .stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
    }

    // ── Write Review ───────────────────────────────────────
    public ReviewResponse writeReview(Long guideId,
                                      CreateReviewRequest req) {
        User user       = getCurrentUser();
        TourGuide guide = findGuide(guideId);

        GuideBooking booking = null;
        if (req.getBookingId() != null) {
            booking = bookingRepository.findById(req.getBookingId())
                    .orElse(null);
        }

        GuideReview review = GuideReview.builder()
                .guide(guide)
                .user(user)
                .booking(booking)
                .rating(req.getRating())
                .comment(req.getComment())
                .build();

        reviewRepository.save(review);

        // Update guide average rating
        Double avgRating = reviewRepository
                .findAverageRatingByGuideId(guideId);
        Long count = reviewRepository.countByGuideId(guideId);
        guide.setRating(avgRating != null
                ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        guide.setReviewCount(count.intValue());
        guideRepository.save(guide);

        log.info("Review added for guide: {}", guide.getFullName());
        return toReviewResponse(review, user);
    }

    // ── Get Guide Reviews ──────────────────────────────────
    public List<ReviewResponse> getGuideReviews(Long guideId) {
        return reviewRepository
                .findByGuideIdOrderByCreatedAtDesc(guideId)
                .stream()
                .map(r -> toReviewResponse(r, r.getUser()))
                .collect(Collectors.toList());
    }

    // ── Helper ─────────────────────────────────────────────
    private TourGuide findGuide(Long id) {
        return guideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Guide not found: " + id));
    }

    // ── MAPPERS ────────────────────────────────────────────
    private GuideResponse toGuideResponse(TourGuide g) {
        GuideResponse res = new GuideResponse();
        res.setId(g.getId());
        res.setFullName(g.getFullName());
        res.setBio(g.getBio());
        res.setPhotoUrl(g.getPhotoUrl());
        res.setLanguages(g.getLanguages());
        res.setSpecialties(g.getSpecialties());
        res.setDistrict(g.getDistrict());
        res.setPricePerDay(g.getPricePerDay());
        res.setVerified(g.getVerified());
        res.setAvailable(g.getAvailable());
        res.setRating(g.getRating());
        res.setReviewCount(g.getReviewCount());
        res.setPhone(g.getPhone());
        res.setWhatsappNumber(g.getWhatsappNumber());
        res.setEmail(g.getEmail());
        res.setImageUrls(g.getImageUrls());
        return res;
    }

    private GuideBookingResponse toBookingResponse(GuideBooking b) {
        GuideBookingResponse res = new GuideBookingResponse();
        res.setId(b.getId());
        res.setGuideId(b.getGuide().getId());
        res.setGuideName(b.getGuide().getFullName());
        res.setGuideDistrict(b.getGuide().getDistrict());
        res.setUserId(b.getUser().getId());
        res.setTripId(b.getTrip() != null ? b.getTrip().getId() : null);
        res.setStartDate(b.getStartDate());
        res.setEndDate(b.getEndDate());
        res.setStatus(b.getStatus());
        res.setTotalCost(b.getTotalCost());
        res.setAdvanceAmount(b.getAdvanceAmount());
        res.setBalanceAmount(b.getBalanceAmount());
        res.setNotes(b.getNotes());
        res.setCreatedAt(b.getCreatedAt());
        return res;
    }

    private ReviewResponse toReviewResponse(GuideReview r, User user) {
        ReviewResponse res = new ReviewResponse();
        res.setId(r.getId());
        res.setGuideId(r.getGuide().getId());
        res.setUserId(user.getId());
        res.setUserName(user.getName());
        res.setRating(r.getRating());
        res.setComment(r.getComment());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }

    private boolean isEffectivelyCompleted(GuideBooking b) {
        return (b.getStatus() == GuideBooking.BookingStatus.COMPLETED ||
                b.getStatus() == GuideBooking.BookingStatus.CONFIRMED)
               && b.getEndDate().isBefore(LocalDate.now());
    }
}
