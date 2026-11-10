package com.irion.irion_backend.domain.auth.infrastructure.kakao;

import com.irion.irion_backend.domain.auth.infrastructure.kakao.dto.KakaoTokenResponse;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.dto.KakaoUserInfoResponse;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.exception.KakaoErrorCode;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class KakaoOAuthClient {

    private static final MediaType FORM_URLENCODED_UTF8 =
            new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8);

    private final KakaoProperties kakaoProperties;
    private final RestClient restClient;

    public KakaoOAuthClient(KakaoProperties kakaoProperties, RestClient.Builder restClientBuilder) {
        this.kakaoProperties = kakaoProperties;

        // 타임아웃 지정
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    // 인가 코드로 카카오 토큰 발급
    public KakaoTokenResponse requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakaoProperties.clientId());
        form.add("redirect_uri", kakaoProperties.redirectUri());
        form.add("code", code);
        form.add("client_secret", kakaoProperties.clientSecret());

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(kakaoProperties.tokenUri())
                    .contentType(FORM_URLENCODED_UTF8)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                        log.warn("[Kakao] 토큰 발급 실패 status={}, body={}", res.getStatusCode(), readBody(res));
                        throw new BusinessException(KakaoErrorCode.KAKAO_INVALID_AUTHORIZATION_CODE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                        log.error("[Kakao] 토큰 발급 서버 오류 status={}, body={}", res.getStatusCode(), readBody(res));
                        throw new BusinessException(KakaoErrorCode.KAKAO_API_ERROR);
                    })
                    .body(KakaoTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                log.error("[Kakao] 토큰 발급 응답에 access_token 없음");
                throw new BusinessException(KakaoErrorCode.KAKAO_API_ERROR);
            }
            return response;
        } catch (ResourceAccessException e) {
            // 연결 실패, 타임 아웃
            log.error("[Kakao] 토큰 발급 요청 실패(네트워크/타임아웃)", e);
            throw new BusinessException(KakaoErrorCode.KAKAO_API_UNAVAILABLE);
        }
    }

    // 카카오 access token으로 사용자 정보 조회
    public KakaoUserInfoResponse requestUserInfo(String kakaoAccessToken) {
        try {
            KakaoUserInfoResponse response = restClient.get()
                    .uri(kakaoProperties.userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                        log.error("[Kakao] 사용자 정보 조회 실패 status={}, body={}", res.getStatusCode(), readBody(res));
                        throw new BusinessException(KakaoErrorCode.KAKAO_API_ERROR);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                        log.error("[Kakao] 사용자 정보 조회 서버 오류 status={}, body={}", res.getStatusCode(), readBody(res));
                        throw new BusinessException(KakaoErrorCode.KAKAO_API_ERROR);
                    })
                    .body(KakaoUserInfoResponse.class);

            if (response == null || response.id() == null) {
                log.error("[Kakao] 사용자 정보 응답에 id 없음");
                throw new BusinessException(KakaoErrorCode.KAKAO_API_ERROR);
            }
            return response;
        } catch (ResourceAccessException e) {
            log.error("[Kakao] 사용자 정보 조회 요청 실패(네트워크/타임아웃)", e);
            throw new BusinessException(KakaoErrorCode.KAKAO_API_UNAVAILABLE);
        }
    }

    // 인가 코드 -> 카카오 토큰 -> 사용자 정보 처리
    public KakaoUserInfoResponse getUserInfo(String code) {
        KakaoTokenResponse token = requestToken(code);
        return requestUserInfo(token.accessToken());
    }


    private String readBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch ( IOException e) {
            return "(본문 읽기 실패)";
        }
    }
}
