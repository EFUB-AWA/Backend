package com.irion.irion_backend.domain.analysis.controller;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunResponse;
import com.irion.irion_backend.domain.analysis.service.AnalysisService;
import com.irion.irion_backend.global.common.response.ErrorResponse;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.auth.AuthMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.EMPTY_PROPERTY_LIST;
import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.INVALID_ANALYSIS_REQUEST;

@RestController
@Profile("analysis-api")
@RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisService analysisService;

    @PostMapping("/api/analyses")
    public ResponseEntity<AnalysisRunResponse> start(@AuthMember Long requesterId,
                                                    @Valid @RequestBody AnalysisRunRequest request,
                                                    BindingResult errors) {
        if (errors.hasErrors()) {
            if (errors.getErrorCount() == 1 && request.candidatePropertyIds() != null
                    && request.candidatePropertyIds().isEmpty()) {
                throw new BusinessException(EMPTY_PROPERTY_LIST);
            }
            throw new BusinessException(INVALID_ANALYSIS_REQUEST);
        }
        return ResponseEntity.accepted().body(analysisService.start(requesterId, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidBody(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(INVALID_ANALYSIS_REQUEST, request.getRequestURI()));
    }
}
