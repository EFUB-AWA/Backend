package com.irion.irion_backend.domain.auth.dev.controller;

import com.irion.irion_backend.domain.auth.dev.dto.DevTokenRequest;
import com.irion.irion_backend.domain.auth.dev.dto.DevTokenResponse;
import com.irion.irion_backend.global.security.auth.AuthMember;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("local")
public class DevTokenController {

    private final JwtProvider jwtProvider;

    @GetMapping("/me")
    public ResponseEntity<Long> getDevUser(@AuthMember Long requesterId) {
        return ResponseEntity.ok(requesterId);
    }

    @PostMapping("/token")
    public ResponseEntity<DevTokenResponse> getDevToken(@Valid @RequestBody DevTokenRequest request) {
        Long userId = request.userId();
        String accessToken = jwtProvider.createAccessToken(userId).token();
        DevTokenResponse response = DevTokenResponse.of(accessToken);

        return ResponseEntity.ok(response);
    }
}
