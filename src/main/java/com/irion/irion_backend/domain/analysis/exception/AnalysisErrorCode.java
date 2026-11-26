package com.irion.irion_backend.domain.analysis.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisErrorCode implements ErrorCode {

    ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 분석은 삭제할 수 없습니다."),
    PROPERTY_CONSULTATION_IN_PROGRESS(HttpStatus.CONFLICT, "매물 AI 컨설팅이 진행 중입니다."),

    INVALID_ANALYSIS_REQUEST(HttpStatus.BAD_REQUEST, "분석 요청이 올바르지 않습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 정보를 찾을 수 없습니다."),
    EMPTY_PROPERTY_LIST(HttpStatus.BAD_REQUEST, "분석 대상 매물을 하나 이상 선택해야 합니다."),
    ANALYSIS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "분석 대상에 접근할 권한이 없습니다."),
    PROPERTY_MAPPING_NOT_COMPLETED(HttpStatus.CONFLICT, "매물의 상권 매핑이 완료되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() { return name(); }
}
