package com.muzin.mu.zin.exception;

import com.muzin.mu.zin.dto.ApiRespDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * 모든 실패 응답을 같은 형태로 통일
 *   HTTP 4xx/5xx + { "status": "failed", "message": "...", "data": { "code": "...", "fieldErrors": {...} } }
 *
 * 서비스 코드 규칙
 *   - 입력값/요청이 잘못됨      -> IllegalArgumentException (400)
 *   - 현재 상태에서 불가능한 요청 -> IllegalStateException (409)
 *   - 상태/코드를 명확히 하고 싶을 때 -> ApiException
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleApi(ApiException e) {
        if (e.getRetryAfterSeconds() != null) {
            // 횟수 제한(429): 표준 Retry-After 헤더 + 본문에도 남은 시간
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                    .body(new ApiRespDto<>("failed", e.getMessage(),
                            new ErrorDetail(e.getCode(), null, e.getRetryAfterSeconds())));
        }
        return fail(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleIllegalArgument(IllegalArgumentException e) {
        return fail(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleIllegalState(IllegalStateException e) {
        return fail(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return fail(status, status.name(), e.getReason() != null ? e.getReason() : status.getReasonPhrase());
    }

    // @Valid 검증 실패 (요청 바디)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String first = fieldErrors.values().stream().findFirst().orElse("입력값을 확인해주세요.");
        return ResponseEntity.badRequest()
                .body(new ApiRespDto<>("failed", first, new ErrorDetail("VALIDATION_FAILED", fieldErrors, null)));
    }

    // 메서드 파라미터 검증 실패 (@RequestParam 등)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleMethodValidation(HandlerMethodValidationException e) {
        return fail(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 값을 확인해주세요.");
    }

    // JSON 형식 오류, enum 값 오타 등
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleNotReadable(HttpMessageNotReadableException e) {
        return fail(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleBadParam(Exception e) {
        return fail(HttpStatus.BAD_REQUEST, "BAD_PARAMETER", "요청 파라미터가 올바르지 않습니다.");
    }

    // 두 요청이 같은 예약/슬롯을 동시에 바꾸려 할 때 (@Version 충돌)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return fail(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "다른 요청이 먼저 처리되었습니다. 새로고침 후 다시 시도해주세요.");
    }

    // DB 제약 위반 (중복 예약 유니크 인덱스 등) - 상세 내용은 로그에만
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return fail(HttpStatus.CONFLICT, "DATA_CONFLICT", "이미 처리되었거나 중복된 요청입니다.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleAuthentication(AuthenticationException e) {
        return fail(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleAccessDenied(AccessDeniedException e) {
        return fail(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return fail(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleNoResource(NoResourceFoundException e) {
        return fail(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 주소를 찾을 수 없습니다.");
    }

    // 예상하지 못한 오류: 내부 메시지는 숨기고 로그에만 남김
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiRespDto<ErrorDetail>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), e);
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    private ResponseEntity<ApiRespDto<ErrorDetail>> fail(HttpStatus status, String code, String message) {
        if (status.is5xxServerError()) {
            log.error("[{}] {}", code, message);
        } else {
            log.debug("[{}] {}", code, message);
        }
        return ResponseEntity.status(status)
                .body(new ApiRespDto<>("failed", message, ErrorDetail.of(code)));
    }
}
