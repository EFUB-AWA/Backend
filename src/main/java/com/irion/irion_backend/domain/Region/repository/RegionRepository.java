package com.irion.irion_backend.domain.Region.repository;

import com.irion.irion_backend.domain.Region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository
        extends JpaRepository<Region, String> {
}
