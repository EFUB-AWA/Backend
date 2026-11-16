package com.irion.irion_backend.domain.costbenchmark.exception;

import com.irion.irion_backend.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CostBenchmarkErrorCode implements ErrorCode {

    INDUSTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 업종입니다."),
    COST_BENCHMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 업종의 평균 운영비 데이터가 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}

