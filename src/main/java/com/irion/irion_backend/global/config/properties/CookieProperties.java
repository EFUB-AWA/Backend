package com.irion.irion_backend.global.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "cookie")
public record CookieProperties(
        boolean secure,

        @NotBlank
        String sameSite
) {

    private static final Set<String> ALLOWED_SAME_SITE = Set.of("Strict", "Lax", "None");

    // 잘못된 설정 조합일 시 서버 시작 시점에 바로 실패 처리
    public CookieProperties {
        if (sameSite != null && !ALLOWED_SAME_SITE.contains(sameSite)) {
            throw new IllegalStateException(
                    "cookie.same-site는 Strict, Lax, None 중 하나여야 합니다. 현재 값: " + sameSite
            );
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalStateException(
                    "cookie.same-site=None면 cookie.secure=true여야 합니다."
            );
        }
    }
}
