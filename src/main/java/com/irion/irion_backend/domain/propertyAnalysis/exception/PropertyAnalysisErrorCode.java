package com.irion.irion_backend.domain.propertyAnalysis.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PropertyAnalysisErrorCode implements ErrorCode {

    PROPERTY_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "매물 분석 결과를 찾을 수 없습니다."),
    ANALYSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 분석이 완료되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}