package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.VehicleReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleReviewRepository
        extends JpaRepository<VehicleReview, Long> {

    List<VehicleReview> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    @Query("SELECT AVG(r.rating) FROM VehicleReview r " +
           "WHERE r.vehicle.id = :vehicleId")
    Double findAverageRatingByVehicleId(@Param("vehicleId") Long vehicleId);

    Long countByVehicleId(Long vehicleId);
}
