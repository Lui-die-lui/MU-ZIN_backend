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
    private String username; // 로그인 아이디
    @JsonIgnore
    private String password;

    private String email;
    private String profileImgUrl;
    private Boolean emailVerified;
    private ArtistStatus artistStatus;

    // 권한 리스트 (User <-> UserRole 연관관계 그대로)
    private List<UserRole> userRoles;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // UserRole -> Role -> roleName 을 GrantedAuthority로 변환
        return userRoles.stream()
                .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().getRoleName()))
                // 권한 명들

                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        // Spring Security 입장에서의 username(로그인 아이디로 무엇을 쓸지 명시)
        return this.email;
    }
}
