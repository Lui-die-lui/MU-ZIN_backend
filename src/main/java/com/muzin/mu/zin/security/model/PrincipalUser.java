package com.muzin.mu.zin.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.UserRole;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class PrincipalUser implements UserDetails {

    private Integer userId;
    private String email;
    private String username; // 로그인 아이디
    @JsonIgnore
    private String password;

    private String profileImgUrl;
    private Boolean emailVerified;
    private ArtistStatus artistStatus;

    // 권한 리스트 (User <-> UserRole 연관관계 그대로)
    // private List<UserRole> userRoles; -> 순환 참조 이슈있음

    // JPA 엔티티 대신 권한 리스트만 들고 있게
    // 시큐리티 내부용
    @JsonIgnore
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // UserRole -> Role -> roleName 을 GrantedAuthority로 변환
//        return userRoles.stream()
//                .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().getRoleName()))
//                // 권한 명들
//
//                .collect(Collectors.toList());
        return this.authorities;
    }

    // 프론트에서 보기 좋게 문자열 리스트로 역할만 따로
    private List<String> roles;

    @Override
    public String getUsername() {
        // Spring Security 입장에서의 username(로그인 아이디로 무엇을 쓸지 명시)
        return this.email;
    }
}
