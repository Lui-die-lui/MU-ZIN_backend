package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.SigninRequest;
import com.muzin.mu.zin.dto.auth.SignupRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    // 일반 200 ok 말고 커스텀 예외로 던져주는 중 - 이메일 중복 확인때문에
    @PostMapping("/signup")
    public ResponseEntity<ApiRespDto<?>> signup(@RequestBody SignupRequest req) {
        authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiRespDto<>("success", "회원가입 성공", null));
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signin(@RequestBody SigninRequest signinRequest) {
        return ResponseEntity.ok(authService.signin(signinRequest));
    }

    // Principal 요청 안돼서 403뜸 Why?
    @GetMapping("/principal")
//    public ResponseEntity<?> getPrincipal() {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        PrincipalUser principalUser = (PrincipalUser) authentication.getPrincipal(); // 이거 string을 principal로 캐스팅하다 터짐
//        // 해당 로직은 웹개발 - 러너스 하이 로직 참조했는데 이 방법으로는 안될듯
//        ApiRespDto<?> apiRespDto = new ApiRespDto<>("success","", principalUser);
//        return ResponseEntity.ok(apiRespDto);
//    }
    public ResponseEntity<?> getPrincipal(@AuthenticationPrincipal PrincipalUser principalUser) {
        if (principalUser == null) {
            ApiRespDto<?> fail = new ApiRespDto<>("failed", "인증되지 않은 사용자입니다.",null);
            return ResponseEntity.status(401).body(fail);
        }

        ApiRespDto<?> apiRespDto = new ApiRespDto<>("success", "", principalUser);
        return ResponseEntity.ok(apiRespDto);
    }

}
