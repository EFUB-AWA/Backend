package com.irion.irion_backend.domain.aiConsultation.property.controller;

import com.irion.irion_backend.domain.aiConsultation.property.dto.response.PropertyConsultationDetailResponse;
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

    @GetMapping("/{propertyConsultId}")
    public ResponseEntity<PropertyConsultationDetailResponse> getPropertyConsultation(@AuthMember Long requesterId,
                                                                                      @PathVariable("propertyConsultId") Long propertyConsultId) {
        return ResponseEntity.ok(propertyConsultationService.getPropertyConsultation(requesterId, propertyConsultId));
    }
}
