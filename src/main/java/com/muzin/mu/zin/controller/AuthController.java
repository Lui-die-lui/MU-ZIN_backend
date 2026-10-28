package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.PrincipalDto;
import com.muzin.mu.zin.dto.auth.SigninRequest;
import com.muzin.mu.zin.dto.auth.ConsentRequest;
import com.muzin.mu.zin.dto.auth.OAuth2SignupRequest;
import com.muzin.mu.zin.dto.auth.SignupRequest;
import com.muzin.mu.zin.dto.auth.WithdrawRequest;
import com.muzin.mu.zin.security.handler.JsonSecurityErrorHandler;
import com.muzin.mu.zin.security.model.PrincipalUser;
import jakarta.servlet.http.HttpServletRequest;
import com.muzin.mu.zin.service.AuthService;
import com.muzin.mu.zin.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final WithdrawalService withdrawalService;

    // 이메일 중복 시 409 (GlobalExceptionHandler)
    // 소셜 첫 가입 완료 (동의 화면에서) -> 로그인 토큰
    @PostMapping("/oauth2/signup")
    public ApiRespDto<String> oauth2Signup(@Valid @RequestBody OAuth2SignupRequest req) {
        return new ApiRespDto<>("success", "가입이 완료되었습니다.", authService.completeOAuth2Signup(req));
    }

    // 기존 회원 동의 (약관이 바뀌었거나 이 기능 이전에 가입한 경우)
    @PostMapping("/consent")
    public ApiRespDto<Void> consent(@RequestBody ConsentRequest req, @AuthenticationPrincipal PrincipalUser principal,
                                    HttpServletRequest request) {
        if (principal == null) throw JsonSecurityErrorHandler.authFailure(request);
        authService.agree(principal, req);
        return ApiRespDto.ok("동의가 저장되었습니다.", null);
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiRespDto<?>> signup(@Valid @RequestBody SignupRequest req) {
        authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiRespDto<>("success", "회원가입 성공", null));
    }

    @PostMapping("/withdraw")
    public ApiRespDto<Void> withdraw(@RequestBody WithdrawRequest req,
                                     @AuthenticationPrincipal PrincipalUser principal,
                                     HttpServletRequest request) {
        if (principal == null) throw JsonSecurityErrorHandler.authFailure(request);
        withdrawalService.withdraw(principal, req);
        return ApiRespDto.ok("회원 탈퇴가 완료되었습니다.", null);
    }

    // 실패 시 401 (이메일/비밀번호 불일치), 403 (정지 계정)
    @PostMapping("/signin")
    public ApiRespDto<String> signin(@Valid @RequestBody SigninRequest signinRequest) {
        return authService.signin(signinRequest);
    }

    // 비로그인 401 / 토큰 만료·위조 401 / 정지 계정 403 (공개 경로라 사유는 JWT 필터 기록에서 가져옴)
    @GetMapping("/principal")
    public ApiRespDto<PrincipalDto> principal(@AuthenticationPrincipal PrincipalUser principal,
                                              HttpServletRequest request) {
        if (principal == null) {
            throw JsonSecurityErrorHandler.authFailure(request);
        }
        return authService.getPrincipalDto(principal);
    }
}
