package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.GuideBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuideBookingRepository
        extends JpaRepository<GuideBooking, Long> {

    List<GuideBooking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<GuideBooking> findByGuideIdOrderByCreatedAtDesc(Long guideId);
    List<GuideBooking> findByTripIdOrderByStartDate(Long tripId);
}