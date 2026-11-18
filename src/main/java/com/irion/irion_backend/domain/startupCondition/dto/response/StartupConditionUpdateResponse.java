package com.irion.irion_backend.domain.startupCondition.dto.response;

import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;

import java.time.LocalDateTime;

public record StartupConditionUpdateResponse(
        Long startupConditionId,
        String title,
        LocalDateTime updatedAt
) {
    public static StartupConditionUpdateResponse from(StartupCondition condition) {
        return new StartupConditionUpdateResponse(
                condition.getId(),
                condition.getTitle(),
                condition.getUpdatedAt()
        );
    }
}