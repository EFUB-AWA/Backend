package com.irion.irion_backend.global.security.jwt;

import org.springframework.util.StringUtils;

public final class BearerTokenResolver {

    private static final String TOKEN_PREFIX = "Bearer ";

    private BearerTokenResolver() {}

    public static String resolve(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(TOKEN_PREFIX)) {
            return authorizationHeader.substring(TOKEN_PREFIX.length());
        }

        return null;
    }

}
