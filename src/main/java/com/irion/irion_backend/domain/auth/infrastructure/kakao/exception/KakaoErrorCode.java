package com.irion.irion_backend.domain.auth.infrastructure.kakao.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum KakaoErrorCode implements ErrorCode {

    KAKAO_INVALID_AUTHORIZATION_CODE(HttpStatus.BAD_REQUEST, "카카오 로그인에 실패했습니다. 다시 시도해주세요."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 API 호출에 실패했습니다."),
    KAKAO_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "카카오 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() { return name(); }
}
