package com.irion.irion_backend.domain.aiConsultation.property.controller;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.aiConsultation.property.exception.PropertyConsultationGenerationException;
import com.irion.irion_backend.global.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.irion.irion_backend.domain.aiConsultation.property.dto.response.PropertyConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.property.dto.PropertyConsultationCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.irion.irion_backend.domain.aiConsultation.property.service.PropertyConsultationService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultations/properties")
@RequiredArgsConstructor
public class PropertyConsultationController {

    private final PropertyConsultationService propertyConsultationService;

    record FailedConsultation(Long propertyConsultId, ConsultationStatus status) {}
    record GenerationError(@JsonUnwrapped ErrorResponse errorResponse, FailedConsultation data) {}

    @ExceptionHandler(PropertyConsultationGenerationException.class)
    ResponseEntity<GenerationError> handleGenerationFailure(PropertyConsultationGenerationException failure,
                                                          HttpServletRequest request) {
        var code = failure.getErrorCode();
        var error = ErrorResponse.of(code, request.getRequestURI());
        var data = new FailedConsultation(failure.getPropertyConsultId(), ConsultationStatus.FAILED);
        return ResponseEntity.status(code.getStatus()).body(new GenerationError(error, data));
    }

    @PostMapping
    public ResponseEntity<PropertyConsultationDetailResponse> create(@AuthMember Long requesterId,
            @Valid @RequestBody PropertyConsultationCreateRequest request) {
        var creation = propertyConsultationService.create(requesterId, request.propertyAnalysisId());
        return ResponseEntity.status(creation.created() ? HttpStatus.CREATED : HttpStatus.OK).body(creation.detail());
    }

    @GetMapping("/{propertyConsultId}")
    public ResponseEntity<PropertyConsultationDetailResponse> getPropertyConsultation(@AuthMember Long requesterId,
                                                                                      @PathVariable("propertyConsultId") Long propertyConsultId) {
        return ResponseEntity.ok(propertyConsultationService.getPropertyConsultation(requesterId, propertyConsultId));
    }
}
