package com.irion.irion_backend.domain.property.controller;

import com.irion.irion_backend.domain.property.dto.request.PropertyCreateRequest;
import com.irion.irion_backend.domain.property.dto.response.PropertyCreateResponse;
import com.irion.irion_backend.domain.property.dto.response.PropertyDetailResponse;
import com.irion.irion_backend.domain.property.dto.response.PropertyListResponse;
import com.irion.irion_backend.domain.property.service.PropertyService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    // 후보 매물 등록
    @PostMapping("/properties")
    public ResponseEntity<PropertyCreateResponse> registerProperty(@AuthMember Long requesterId,
                                                                   @Valid @RequestBody PropertyCreateRequest request) {
        PropertyCreateResponse response = propertyService.registerProperty(requesterId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 후보 매물 목록 조회
    @GetMapping("/startup-conditions/{startupConditionId}/properties")
    public ResponseEntity<PropertyListResponse> getProperties(@AuthMember Long requesterId,
                                                              @PathVariable("startupConditionId") Long conditionId) {
        return ResponseEntity.ok(propertyService.getProperties(requesterId, conditionId));
    }

    // 후보 매물 상세 조회
    @GetMapping("/properties/{propertyId}")
    public ResponseEntity<PropertyDetailResponse> getProperty(@AuthMember Long requesterId,
                                                              @PathVariable("propertyId") Long propertyId) {
        return ResponseEntity.ok(propertyService.getProperty(requesterId, propertyId));
    }
}
