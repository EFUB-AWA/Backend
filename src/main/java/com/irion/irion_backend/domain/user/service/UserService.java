package com.irion.irion_backend.domain.user.service;

import com.irion.irion_backend.domain.user.dto.NicknameUpdateRequest;
import com.irion.irion_backend.domain.user.dto.NicknameUpdateResponse;
import com.irion.irion_backend.domain.user.dto.UserResponse;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.domain.user.repository.UserRepository;
import com.irion.irion_backend.domain.user.exception.UserErrorCode;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getMyInfo(Long requesterId) {
        User user = findUser(requesterId);
        return UserResponse.from(user);
    }

    @Transactional
    public NicknameUpdateResponse updateNickname(Long requesterId, NicknameUpdateRequest request) {
        User user = findUser(requesterId);

        // 기존 닉네임과 같으면 현재 정보를 그대로 반환
        if (user.getNickname().equals(request.nickname())) {
            return NicknameUpdateResponse.from(user);
        }

        user.changeNickname(request.nickname());

        // 변경 사항을 반영하여 갱신된 updatedAt을 응답에 사용
        userRepository.flush();

        return NicknameUpdateResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
