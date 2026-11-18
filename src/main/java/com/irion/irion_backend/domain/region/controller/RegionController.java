package com.irion.irion_backend.domain.region.controller;

import com.irion.irion_backend.domain.region.dto.RegionListResponse;
import com.irion.irion_backend.domain.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public RegionListResponse getRegions() {
        return regionService.getRegions();
    }
}
