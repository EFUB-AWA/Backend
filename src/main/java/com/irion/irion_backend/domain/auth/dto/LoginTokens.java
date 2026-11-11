package com.irion.irion_backend.domain.auth.dto;

import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.security.jwt.IssuedToken;

public record LoginTokens(
        IssuedToken accessToken,
        IssuedToken refreshToken,
        User user,
        boolean isNewUser
) {

    public static LoginTokens of(IssuedToken accessToken, IssuedToken refreshToken, LoginResult res) {
        return new LoginTokens(
                accessToken, refreshToken, res.user(), res.isNewUser()
        );
    }
}
