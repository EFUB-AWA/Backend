package com.irion.irion_backend.global.common.response;

import com.irion.irion_backend.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path
) {
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.getMessage(), path);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        HttpStatus status = errorCode.getStatus();
        return new ErrorResponse(
                OffsetDateTime.now(ZoneId.of("Asia/Seoul")),
                status.value(),
                status.getReasonPhrase(),
                errorCode.getCode(),
                message,
                path
        );
    }
}
