package com.irion.irion_backend.domain.aiConsultation.comparison.controller;

import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationListResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationSaveResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.service.ComparisonConsultationService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public ResponseEntity<ComparisonConsultationListResponse> getComparisonConsultations(@AuthMember Long requesterId,
                                                                                         @RequestParam(required = false, name = "cursor") Long cursor,
                                                                                         @Min(1) @Max(50) @RequestParam(required = false, defaultValue = "10", name = "size") int size) {
        return ResponseEntity.ok(comparisonConsultationService
                .getComparisonConsultations(requesterId, cursor, size));
    }

    @PostMapping("/{comparisonConsultId}/save")
    public ResponseEntity<ComparisonConsultationSaveResponse> saveComparisonConsultation(@AuthMember Long requesterId,
                                                                                         @PathVariable("comparisonConsultId") Long comparisonConsultationId) {
        return ResponseEntity.ok(comparisonConsultationService.saveComparisonConsultation(requesterId, comparisonConsultationId));
    }
}
