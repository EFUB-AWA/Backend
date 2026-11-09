package com.irion.irion_backend.domain.user.controller;

import com.irion.irion_backend.domain.user.dto.NicknameUpdateRequest;
import com.irion.irion_backend.domain.user.dto.NicknameUpdateResponse;
import com.irion.irion_backend.domain.user.dto.UserResponse;
import com.irion.irion_backend.domain.user.service.UserService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(
            @AuthMember Long requesterId
    ) {
        return ResponseEntity.ok(userService.getMyInfo(requesterId));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<NicknameUpdateResponse> updateNickname(
            @AuthMember Long requesterId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateNickname(requesterId, request));
    }
}
