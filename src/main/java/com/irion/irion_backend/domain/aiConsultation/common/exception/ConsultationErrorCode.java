package com.irion.irion_backend.domain.aiConsultation.common.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ConsultationErrorCode implements ErrorCode {

    CONSULTATION_NOT_FOUND(HttpStatus.NOT_FOUND, "컨설팅을 찾을 수 없습니다."),
    CONSULTATION_EXPIRED(HttpStatus.NOT_FOUND, "해당 컨설팅은 만료되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() { return name(); }
}
