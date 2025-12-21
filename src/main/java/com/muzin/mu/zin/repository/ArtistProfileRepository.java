package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.ArtistProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {

    // 결과가 있을 수도 없을 수도 있는 user의 userId = userId 인것을 찾음
    // ArtistProfile.user.userId 로 조건을 건다
    Optional<ArtistProfile> findByUser_UserId(Long userId);

    // 해당 유저가 존재 하는가
    boolean existsByUser_UserId(Long userId);
}
