package com.irion.irion_backend.domain.user.dto;

import com.irion.irion_backend.domain.user.entity.User;

import java.time.LocalDateTime;

public record NicknameUpdateResponse(
        String nickname,
        LocalDateTime updatedAt
) {

    public static NicknameUpdateResponse from(User user) {
        return new NicknameUpdateResponse(
                user.getNickname(),
                user.getUpdatedAt()
        );
    }
}
