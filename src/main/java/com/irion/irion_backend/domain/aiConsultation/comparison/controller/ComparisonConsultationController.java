package com.irion.irion_backend.domain.aiConsultation.comparison.controller;

import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationListResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationSaveResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.service.ComparisonConsultationService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.ComparisonConsultationCreateRequest;
import com.irion.irion_backend.domain.aiConsultation.comparison.exception.ComparisonGenerationException;
import com.irion.irion_backend.global.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    record FailedConsultation(Long comparisonConsultId, ConsultationStatus status) {}
    record GenerationError(@JsonUnwrapped ErrorResponse errorResponse, FailedConsultation data) {}

    @ExceptionHandler(ComparisonGenerationException.class)
    ResponseEntity<GenerationError> handleGenerationFailure(ComparisonGenerationException failure,
                                                          HttpServletRequest request) {
        var code = failure.getErrorCode();
        var error = ErrorResponse.of(code, request.getRequestURI());
        var data = new FailedConsultation(failure.getComparisonConsultId(), ConsultationStatus.FAILED);
        return ResponseEntity.status(code.getStatus()).body(new GenerationError(error, data));
    }

    @PostMapping
    public ResponseEntity<ComparisonConsultationDetailResponse> create(@AuthMember Long requesterId,
            @Valid @RequestBody ComparisonConsultationCreateRequest request) {
        var creation = comparisonConsultationService.create(requesterId, request.propertyAnalysisIds());
        return ResponseEntity.status(creation.created() ? HttpStatus.CREATED : HttpStatus.OK).body(creation.detail());
    }

    @PostMapping("/{comparisonConsultId}/retry")
    public ResponseEntity<ComparisonConsultationDetailResponse> retry(@AuthMember Long requesterId,
            @PathVariable("comparisonConsultId") Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comparisonConsultationService.retry(requesterId, id));
    }

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
