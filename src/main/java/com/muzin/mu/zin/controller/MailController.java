package com.muzin.mu.zin.controller;


import com.muzin.mu.zin.dto.mail.SendMailRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/send")
    public ResponseEntity<?> sendMail(@RequestBody SendMailRequest sendMailRequest,
                                      @AuthenticationPrincipal PrincipalUser principalUser) {
        return ResponseEntity.ok(mailService.sendMail(sendMailRequest, principalUser));
    }

    // 메일 속 링크(브라우저 이동)로 들어오므로 JSON 대신 프론트 결과 페이지로 리다이렉트
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam(required = false) String verifyToken) {
        String result = (verifyToken == null || verifyToken.isBlank())
                ? MailService.INVALID
                : mailService.verify(verifyToken);

        String target = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/mail/verify-result")
                .queryParam("result", result)
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target)
                .build();
    }
}
