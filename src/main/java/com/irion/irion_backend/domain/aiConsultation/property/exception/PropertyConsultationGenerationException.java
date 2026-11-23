package com.irion.irion_backend.domain.aiConsultation.property.exception;

import com.irion.irion_backend.global.error.BusinessException;
import lombok.Getter;

@Getter
public class PropertyConsultationGenerationException extends BusinessException {
    private final Long propertyConsultId;

    public PropertyConsultationGenerationException(Long propertyConsultId, BusinessException cause) {
        super(cause.getErrorCode());
        this.propertyConsultId = propertyConsultId;
        initCause(cause);
    }
}
