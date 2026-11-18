package com.irion.irion_backend.domain.startupCondition.dto.response;

import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;

import java.time.LocalDateTime;

public record StartupConditionCreateResponse(Long startupConditionId, String title, LocalDateTime createdAt) {
    public static StartupConditionCreateResponse from(StartupCondition condition) {
        return new StartupConditionCreateResponse(
                condition.getId(),
                condition.getTitle(),
                condition.getCreatedAt()
        );
    }
}

