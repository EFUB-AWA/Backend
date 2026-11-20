package com.irion.irion_backend.domain.aiConsultation.comparison.controller;

import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.service.ComparisonConsultationService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultations/comparisons")
@RequiredArgsConstructor
public class ComparisonConsultationController {

    private final ComparisonConsultationService comparisonConsultationService;

    @GetMapping("/{comparisonConsultId}")
    public ResponseEntity<ComparisonConsultationDetailResponse> getComparisonConsultation(@AuthMember Long requesterId,
                                                                                          @PathVariable("comparisonConsultId") Long comparisonConsultationId) {
        return ResponseEntity.ok(comparisonConsultationService
                .getComparisonConsultation(requesterId, comparisonConsultationId));
    }
}
