package com.irion.irion_backend.domain.aiConsultation.comparison.exception;

import com.irion.irion_backend.global.error.BusinessException;
import lombok.Getter;

@Getter
public class ComparisonGenerationException extends BusinessException {
    private final Long comparisonConsultId;

    public ComparisonGenerationException(Long comparisonConsultId, BusinessException cause) {
        super(cause.getErrorCode());
        this.comparisonConsultId = comparisonConsultId;
        initCause(cause);
    }
}
