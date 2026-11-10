package com.irion.irion_backend.domain.user.dto;

import com.irion.irion_backend.domain.user.entity.User;

public record UserResponse(
        Long userId,
        String nickname,
        String email,
        String profileImageUrl
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl()
        );
    }
}
