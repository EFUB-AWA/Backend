package com.irion.irion_backend.domain.industry.controller;

import com.irion.irion_backend.domain.industry.dto.IndustryListResponse;
import com.irion.irion_backend.domain.industry.service.IndustryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/industries")
public class IndustryController {

    private final IndustryService industryService;

    @GetMapping
    public IndustryListResponse getIndustries() {
        return industryService.getIndustries();
    }
}
