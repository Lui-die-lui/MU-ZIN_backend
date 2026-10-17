package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.OAuth2UserEntity;
import com.muzin.mu.zin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OAuth2UserRepository extends JpaRepository<OAuth2UserEntity, Long> {

    // Signin: provider + providerUserId로 연동(OAuth2User) 존재 여부 확인
    Optional<OAuth2UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    // Merge(추후): 특정 유저가 해당 provider를 이미 연동했는지 체크 (중복 연동 방지)
    boolean existsByUserAndProvider(User user, String provider);

    // 유저가 연동한 소셜 목록 조회 - 한 유저에 여러 provider가 붙어있을 수 있으므로 List로 받음
    List<OAuth2UserEntity> findAllByUser_UserId(Long userId);

    // oauth2 User 존재 여부(provider까지 전달할 필요 없어서 그냥 새로 만듦)
    boolean existsByUser_UserId(Long userId);
}
