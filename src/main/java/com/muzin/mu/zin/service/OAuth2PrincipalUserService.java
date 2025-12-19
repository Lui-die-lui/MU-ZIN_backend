package com.muzin.mu.zin.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OAuth2PrincipalUserService extends DefaultOAuth2UserService {
    // OAuth2 provider 의 UserInfo API 호출 -> 사용자 프로필 데이터 받아옴 -> OAuth2User 만들어줌

    @Override
    @SuppressWarnings("unchecked") // Object가 Map인지 확신하지 못하는 unchecked cast 경고 무시
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 로그인 성공 과정 중 자동으로 호출되는 메서드

        OAuth2User oAuth2User = super.loadUser(userRequest); // 바꿔주는 기본 기능을 해줌
        // provider에서 받아온 raw attributes가 여기 들어있음 - 아래에서 Map으로 받아옴


        String provider = userRequest.getClientRegistration().getRegistrationId(); // google, naver, kakao -  이후 switch에서 파싱을 달리 함
        Map<String, Object> attributes = oAuth2User.getAttributes(); // attributes 꺼내기

        String providerUserId = null;
        String email = null;

        switch (provider) {
            case "google" -> {
                // 구글 = 구글 고유 아이디가 sub
                providerUserId = String.valueOf(attributes.get("sub")); // String 일 때도 있고 Long일 때도 있어서 캐스팅하다 터질 수 있음 - 그래서 애초에 String 으로 변환 후 받아버림
                email = (String) attributes.get("email"); // null오류 안뜸. 그냥 null로 나옴
            }
            case "naver" -> {
                // 네이버 = 최상위에 response라는 객체가 한 겹 더 있음
                Map<String, Object> resp = (Map<String, Object>) attributes.get("response");
                providerUserId = resp == null ? null : String.valueOf(resp.get("id")); // null이 아니면 id가져옴
                email = resp == null ? null : (String) resp.get("email");
            }
            case "kakao" -> {
                // 카카오 = 고유 아이디가 id
                // 이메일은 kakao_account.email안에 있음.
                providerUserId = String.valueOf(attributes.get("id"));
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                email = kakaoAccount == null ? null : (String) kakaoAccount.get("email");
            }
            default -> throw new OAuth2AuthenticationException("존재하지 않는 provider : " + provider);
        }

        // 표준화 시켜줌
        Map<String, Object> newAttributes = Map.of(
                "provider", provider,
                "providerUserId", providerUserId,
                "email", email
        );

        // 권한 부여
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_USER"));

        // DefaultOAuth2User 반환
        return new DefaultOAuth2User(authorities, newAttributes, "providerUserId");
    }
}
