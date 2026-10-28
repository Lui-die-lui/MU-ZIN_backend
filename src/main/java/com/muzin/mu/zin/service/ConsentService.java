package com.muzin.mu.zin.service;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.auth.ConsentRequest;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.ConsentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 가입 동의 (이용약관 · 개인정보 수집·이용 · 만 14세 이상)
 *  - 지금 약관·처리방침 버전에 동의한 기록이 없으면 consentRequired -> 화면이 동의 화면으로 보냄
 *  - 약관을 바꾸면 app.legal.*-version 을 올림 -> 기존 회원은 다음 로그인 때 다시 동의
 */
@Service
public class ConsentService {

    private final ConsentRepository repo;
    private final String termsVersion;
    private final String privacyVersion;
    private final boolean signupRequired;

    public ConsentService(
            ConsentRepository repo,
            @Value("${app.legal.terms-version}") String termsVersion,
            @Value("${app.legal.privacy-version}") String privacyVersion,
            @Value("${app.legal.signup-consent-required:false}") boolean signupRequired
    ) {
        this.repo = repo;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.signupRequired = signupRequired;
    }

    public boolean isRequired(long userId) {
        return repo.latest(userId)
                .map(v -> !termsVersion.equals(v.terms()) || !privacyVersion.equals(v.privacy()))
                .orElse(true);
    }

    @Transactional
    public void agree(long userId, ConsentRequest req) {
        requireAll(req);
        repo.insert(userId, termsVersion, privacyVersion, true, TimeDefaults.nowKst());
    }

    // 셋 다 동의했는지만 확인 (저장 안 함)
    public void requireAll(ConsentRequest req) {
        if (!allAgreed(req)) {
            throw ApiException.badRequest("이용약관, 개인정보 수집·이용, 만 14세 이상 확인에 모두 동의해 주세요.");
        }
    }

    /*
     * 이메일 가입 요청의 동의 항목 확인
     *  - 셋 다 동의: true (가입 후 기록)
     *  - 하나라도 "동의 안 함"(false): 거부
     *  - 동의 항목이 아예 없음(배포 전 화면이 보낸 요청): signup-consent-required=false 면 받아 두고 다음 로그인 때 동의 화면,
     *    true 면 거부
     */
    public boolean checkSignup(ConsentRequest req) {
        boolean missing = req == null || (req.agreeTerms() == null && req.agreePrivacy() == null && req.ageOver14() == null);
        if (missing) {
            if (signupRequired) throw ApiException.badRequest("이용약관과 개인정보 수집·이용에 동의해 주세요. 화면을 새로고침한 뒤 다시 시도해 주세요.");
            return false;
        }
        requireAll(req);
        return true;
    }

    private static boolean allAgreed(ConsentRequest req) {
        return req != null && Boolean.TRUE.equals(req.agreeTerms()) && Boolean.TRUE.equals(req.agreePrivacy())
                && Boolean.TRUE.equals(req.ageOver14());
    }
}
