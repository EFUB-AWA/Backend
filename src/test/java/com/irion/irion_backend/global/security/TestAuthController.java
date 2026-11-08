package com.irion.irion_backend.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
public class TestAuthController {

    @GetMapping("/api/test/me")
    public Long getMe(@AuthenticationPrincipal Long userId) {
        return userId;
    }
}
