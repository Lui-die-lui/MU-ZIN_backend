package com.muzin.mu.zin.controller;


import com.muzin.mu.zin.dto.mail.SendMailRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    @PostMapping("/send")
    public ResponseEntity<?> sendMail(@RequestBody SendMailRequest sendMailRequest,
                                      @AuthenticationPrincipal PrincipalUser principalUser) {
        System.out.println("요청 들어옴");
        return ResponseEntity.ok(mailService.sendMail(sendMailRequest, principalUser));
    }

    @GetMapping("/verify")
    public String verify(Model model, @RequestParam String verifyToken) {
        Map<String, Object> resultMap = mailService.verify(verifyToken);
        model.addAllAttributes(resultMap);
        return "result_page";
    }
}
