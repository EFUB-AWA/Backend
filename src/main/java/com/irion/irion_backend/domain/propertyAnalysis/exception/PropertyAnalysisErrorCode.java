package com.irion.irion_backend.domain.propertyAnalysis.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PropertyAnalysisErrorCode implements ErrorCode {

    PROPERTY_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "매물 분석 결과를 찾을 수 없습니다."),
    PROPERTY_ANALYSIS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 매물 분석에 접근할 수 없습니다."),
    STRESS_TEST_NOT_READY(HttpStatus.CONFLICT, "스트레스 테스트 결과가 아직 생성되지 않았습니다."),
    STRESS_TEST_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "스트레스 테스트 결과 조회에 실패했습니다."),
    ANALYSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 분석이 완료되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
