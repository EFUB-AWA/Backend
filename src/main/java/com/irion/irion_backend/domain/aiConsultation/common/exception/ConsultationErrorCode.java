package com.irion.irion_backend.domain.aiConsultation.common.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ConsultationErrorCode implements ErrorCode {

    PROPERTY_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "후보 매물 분석이 존재하지 않습니다."),
    CONSULTATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ANALYSIS_NOT_READY(HttpStatus.CONFLICT, "컨설팅에 필요한 분석 결과가 준비되지 않았습니다."),
    AI_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 서비스 설정을 확인해주세요."),
    JSON_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 컨설팅 JSON 파싱에 실패했습니다."),
    AI_CONSULTATION_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 컨설팅 생성에 실패했습니다."),
    AI_CONSULTATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 컨설팅 생성에 실패했습니다."),

    CONSULTATION_NOT_FOUND(HttpStatus.NOT_FOUND, "컨설팅을 찾을 수 없습니다."),
    CONSULTATION_EXPIRED(HttpStatus.NOT_FOUND, "해당 컨설팅은 만료되었습니다."),
    CONSULTATION_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 생성되지 않은 컨설팅입니다."),
    CONSULTATION_FAILED(HttpStatus.CONFLICT, "생성에 실패한 컨설팅은 저장할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() { return name(); }
}
