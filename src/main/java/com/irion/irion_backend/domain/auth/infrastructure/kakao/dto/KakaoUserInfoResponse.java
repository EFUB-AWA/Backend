package com.irion.irion_backend.domain.auth.infrastructure.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(
        @JsonProperty("id")
        Long id,

        @JsonProperty("connected_at")
        String connectedAt,

        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            @JsonProperty("profile")
            Profile profile,

            @JsonProperty("email")
            String email,

            @JsonProperty("is_email_verified")
            Boolean isEmailVerified,

            @JsonProperty("is_email_valid")
            Boolean isEmailValid
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            @JsonProperty("nickname")
            String nickname,

            @JsonProperty("profile_image_url")
            String profileImageUrl,

            @JsonProperty("thumbnail_image_url")
            String thumbnailImageUrl,

            @JsonProperty("is_default_image")
            Boolean isDefaultImage
    ) {}


    // ------- helper -------
    public String nickname() {
        Profile profile = profile();
        return profile == null ? null : profile.nickname();
    }

    public String profileImageUrl() {
        Profile profile = profile();
        if (profile == null || Boolean.TRUE.equals(profile.isDefaultImage())) {
            return null;
        }
        return profile.profileImageUrl();
    }

    public boolean hasVerifiedEmail() {
        return kakaoAccount() != null
                && Boolean.TRUE.equals(kakaoAccount().isEmailVerified())
                && Boolean.TRUE.equals(kakaoAccount().isEmailValid());
    }

    public String email() {
        return kakaoAccount == null ? null : kakaoAccount().email();
    }

    private Profile profile() {
        return kakaoAccount == null ? null : kakaoAccount.profile();
    }

    @Override
    public String toString() {
        return "KakaoUserInfoResponse[id=" + id + "]";
    }
}
