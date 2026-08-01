package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    long countByAvailableTrue();
    
    // Search by district
    List<Vehicle> findByDistrictIgnoreCaseAndAvailableTrue(String district);

    // Search by type
    List<Vehicle> findByTypeAndAvailableTrue(Vehicle.VehicleType type);

    // Search by district + type
    List<Vehicle> findByDistrictIgnoreCaseAndTypeAndAvailableTrue(
            String district, Vehicle.VehicleType type);

    // Driver included
    List<Vehicle> findByDriverIncludedTrueAndAvailableTrue();

    // Price range
    List<Vehicle> findByPricePerDayBetweenAndAvailableTrue(
            Double minPrice, Double maxPrice);

    // Search by district + price range
    @Query("SELECT v FROM Vehicle v WHERE " +
           "LOWER(v.district) = LOWER(:district) AND " +
           "v.available = true AND " +
           "(:minPrice IS NULL OR v.pricePerDay >= :minPrice) AND " +
           "(:maxPrice IS NULL OR v.pricePerDay <= :maxPrice) AND " +
           "(:type IS NULL OR v.type = :type)")
    List<Vehicle> searchVehicles(
            @Param("district")  String district,
            @Param("minPrice")  Double minPrice,
            @Param("maxPrice")  Double maxPrice,
            @Param("type")      Vehicle.VehicleType type);

    // All available
    List<Vehicle> findByAvailableTrue();
}