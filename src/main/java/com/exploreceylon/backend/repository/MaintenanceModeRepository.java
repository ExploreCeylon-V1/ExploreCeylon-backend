package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.MaintenanceMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceModeRepository extends JpaRepository<MaintenanceMode, Long> {
}
