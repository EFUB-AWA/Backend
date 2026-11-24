package com.irion.irion_backend.domain.propertyAnalysis.controller;

import com.irion.irion_backend.domain.propertyAnalysis.dto.PropertyAnalysisDetailResponse;
import com.irion.irion_backend.domain.propertyAnalysis.service.PropertyAnalysisQueryService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/property-analyses")
@RequiredArgsConstructor
public class PropertyAnalysisQueryController {

    private final PropertyAnalysisQueryService propertyAnalysisQueryService;

    @GetMapping("/{propertyAnalysisId}")
    public PropertyAnalysisDetailResponse getDetail(@AuthMember Long requesterId, @PathVariable("propertyAnalysisId") Long propertyAnalysisId) {
        return propertyAnalysisQueryService.getDetail(requesterId, propertyAnalysisId);
    }
}
