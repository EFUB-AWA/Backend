package com.irion.irion_backend.domain.startupCondition.controller;

import com.irion.irion_backend.domain.startupCondition.dto.request.StartupConditionCreateRequest;
import com.irion.irion_backend.domain.startupCondition.dto.response.StartupConditionCreateResponse;
import com.irion.irion_backend.domain.startupCondition.dto.response.StartupConditionDetailResponse;
import com.irion.irion_backend.domain.startupCondition.service.StartupConditionService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/startup-conditions")
@RequiredArgsConstructor
public class StartupConditionController {

    private final StartupConditionService startupConditionService;

    // 창업 조건 등록
    @PostMapping
    public ResponseEntity<StartupConditionCreateResponse> createCondition(@AuthMember Long requesterId, @Valid @RequestBody StartupConditionCreateRequest request) {

        StartupConditionCreateResponse response = startupConditionService.createCondition(requesterId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //창업 조건 조회
    @GetMapping("/me")
    public ResponseEntity<StartupConditionDetailResponse> getMyCondition(@AuthMember Long requesterId) {
        StartupConditionDetailResponse response = startupConditionService.getMyCondition(requesterId);

        return ResponseEntity.ok(response);
    }
}
