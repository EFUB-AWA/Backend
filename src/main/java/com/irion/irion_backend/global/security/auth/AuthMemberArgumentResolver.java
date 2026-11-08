package com.irion.irion_backend.global.security.auth;

import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.global.error.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {

        boolean hasAuthMemberAnnotation = parameter.hasParameterAnnotation(AuthMember.class);
        boolean hasLongType = Long.class.equals(parameter.getParameterType());

        return hasAuthMemberAnnotation && hasLongType;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        } else {
            throw new BusinessException(AuthErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
