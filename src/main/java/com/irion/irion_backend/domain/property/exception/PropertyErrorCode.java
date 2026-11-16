package com.irion.irion_backend.domain.property.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PropertyErrorCode implements ErrorCode {

    PROPERTY_NOT_FOUND(HttpStatus.NOT_FOUND, "후보 매물을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() { return name(); }
}
