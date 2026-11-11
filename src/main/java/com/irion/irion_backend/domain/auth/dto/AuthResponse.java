package com.irion.irion_backend.domain.auth.dto;

import com.irion.irion_backend.domain.user.entity.User;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        UserProfile user
) {
    private static final String BEARER = "Bearer";

    public record UserProfile(
            Long userId,
            String nickname,
            String email,
            String profileImageUrl
    ) {
        public static UserProfile from(User user) {
            return new UserProfile(
                    user.getId(),
                    user.getNickname(),
                    user.getEmail(),
                    user.getProfileImageUrl()
            );
        }
    }

    public static AuthResponse from(LoginTokens tokens) {
        return new AuthResponse(
                tokens.accessToken().token(),
                BEARER,
                tokens.accessToken().expiresIn().toSeconds(),
                tokens.isNewUser(),
                UserProfile.from(tokens.user())
        );
    }
}
