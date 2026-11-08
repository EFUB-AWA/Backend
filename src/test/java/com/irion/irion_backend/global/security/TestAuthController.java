package com.irion.irion_backend.global.security;

import com.irion.irion_backend.global.security.auth.AuthMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestAuthController {

    @GetMapping("/api/test/me")
    public Long getMe(@AuthMember Long requesterId) {
        return requesterId;
    }
}
