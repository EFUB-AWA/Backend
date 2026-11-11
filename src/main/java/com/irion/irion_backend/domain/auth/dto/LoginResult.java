package com.irion.irion_backend.domain.auth.dto;

import com.irion.irion_backend.domain.user.entity.User;

public record LoginResult(
        User user,
        boolean isNewUser
) {
}
