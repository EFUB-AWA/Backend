package com.irion.irion_backend.domain.user.entity;

import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Column(name = "login_email", nullable = false, unique = true)
    private String email;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    public static User create(
            String nickname,
            String email,
            String profileImageUrl
    ) {
        User user = new User();
        user.nickname = nickname;
        user.email = email;
        user.profileImageUrl = profileImageUrl;
        return user;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}