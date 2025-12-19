package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.OAuth2UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OAuth2UserRepository extends JpaRepository<OAuth2UserEntity, Long> {

    // Signin: provider + providerUserId로 연동(OAuth2User) 존재 여부 확인
    Optional<OAuth2UserEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    // Merge(추후): 특정 유저가 해당 provider를 이미 연동했는지 체크 (중복 연동 방지)
    boolean existsByUser_UserIdAndProvider(Long userId, String provider);

    // Merge(추후): 유저가 연동한 소셜 목록 조회(설정/마이페이지에서 표시)
    List<OAuth2UserEntity> findAllByUser_UserId(Long userId);

}
