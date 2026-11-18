package com.irion.irion_backend.domain.region.repository;

import com.irion.irion_backend.domain.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository
        extends JpaRepository<Region, String> {
}
