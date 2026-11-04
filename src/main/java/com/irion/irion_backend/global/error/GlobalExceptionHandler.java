package com.irion.irion_backend.global.error;

import com.irion.irion_backend.global.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 (직접 던진 예외)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException - code: {}, path: {}", code.getCode(), req.getRequestURI());
        return toResponse(code, code.getMessage(), req);
    }

    // @Valid @RequestBody 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                      HttpServletRequest req) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(GlobalErrorCode.INVALID_INPUT.getMessage());
        return toResponse(GlobalErrorCode.INVALID_INPUT, message, req);
    }

    // @Validated + @PathVariable/@RequestParam 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                   HttpServletRequest req) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(GlobalErrorCode.INVALID_INPUT.getMessage());
        return toResponse(GlobalErrorCode.INVALID_INPUT, message, req);
    }

    // 메서드 파라미터 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e,
                                                                       HttpServletRequest req) {
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse(GlobalErrorCode.INVALID_INPUT.getMessage());
        return toResponse(GlobalErrorCode.INVALID_INPUT, message, req);
    }

    // @ModelAttribute / 쿼리 파라미터 객체 바인딩 실패
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e, HttpServletRequest req) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? String.format("'%s' 요청 값이 올바르지 않습니다.", fieldError.getField())
                : GlobalErrorCode.INVALID_TYPE.getMessage();
        return toResponse(GlobalErrorCode.INVALID_TYPE, message, req);
    }

    // 경로 변수/쿼리 파라미터 타입 불일치 (예: /api/properties/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                            HttpServletRequest req) {
        String message = String.format("'%s' 요청 값의 타입이 올바르지 않습니다.", e.getName());
        return toResponse(GlobalErrorCode.INVALID_TYPE, message, req);
    }

    // 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e,
                                                                HttpServletRequest req) {
        String message = String.format("'%s' 파라미터가 누락되었습니다.", e.getParameterName());
        return toResponse(GlobalErrorCode.MISSING_PARAMETER, message, req);
    }

    // Request Body JSON 변환 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                      HttpServletRequest req) {
        if (e.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {
            String fieldName = ife.getPath().stream()
                    .findFirst()
                    .map(reference -> reference.getPropertyName())
                    .orElse("field");
            String message = String.format("'%s' 값이 올바르지 않습니다.", fieldName);
            return toResponse(GlobalErrorCode.INVALID_ENUM_VALUE, message, req);
        }
        return toResponse(GlobalErrorCode.INVALID_REQUEST_BODY,
                GlobalErrorCode.INVALID_REQUEST_BODY.getMessage(), req);
    }

    // 존재하지 않는 URL
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
        return toResponse(GlobalErrorCode.API_NOT_FOUND, GlobalErrorCode.API_NOT_FOUND.getMessage(), req);
    }

    // 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                  HttpServletRequest req) {
        return toResponse(GlobalErrorCode.METHOD_NOT_ALLOWED,
                GlobalErrorCode.METHOD_NOT_ALLOWED.getMessage(), req);
    }

    // 예상하지 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception - path: {}", req.getRequestURI(), e);
        return toResponse(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                GlobalErrorCode.INTERNAL_SERVER_ERROR.getMessage(), req);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code, String message, HttpServletRequest req) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, message, req.getRequestURI()));
    }
}