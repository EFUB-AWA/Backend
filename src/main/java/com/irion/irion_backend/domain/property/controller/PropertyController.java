package com.irion.irion_backend.domain.property.controller;

import com.irion.irion_backend.domain.property.dto.request.PropertyCreateRequest;
import com.irion.irion_backend.domain.property.dto.response.PropertyCreateResponse;
import com.irion.irion_backend.domain.property.service.PropertyService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    public ResponseEntity<PropertyCreateResponse> registerProperty(@AuthMember Long requesterId,
                                                                   @Valid @RequestBody PropertyCreateRequest request) {
        PropertyCreateResponse response = propertyService.registerProperty(requesterId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
