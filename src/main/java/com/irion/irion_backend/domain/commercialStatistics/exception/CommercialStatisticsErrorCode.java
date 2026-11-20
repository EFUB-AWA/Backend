package com.irion.irion_backend.domain.commercialStatistics.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommercialStatisticsErrorCode implements ErrorCode {

    SEOUL_API_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "서울시 Open API 인증키가 설정되지 않았습니다."),
    SEOUL_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "서울시 Open API 요청에 실패했습니다."),
    SEOUL_API_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "서울시 Open API 응답 형식이 올바르지 않습니다."),
    COMMERCIAL_STATS_CACHE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "상권 통계 캐시를 처리하지 못했습니다."),
    SALES_QUARTER_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "조회할 수 있는 추정매출 분기가 준비되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
