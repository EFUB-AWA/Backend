package com.irion.irion_backend.domain.Region.service;

import com.irion.irion_backend.domain.Region.dto.RegionListResponse;
import com.irion.irion_backend.domain.Region.dto.RegionResponse;
import com.irion.irion_backend.domain.Region.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionListResponse getRegions() {
        return new RegionListResponse(
                regionRepository.findAll().stream()
                        .map(RegionResponse::from)
                        .toList()
        );
    }
}
