package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {

    // 결과가 있을 수도 없을 수도 있는 user의 userId = userId 인것을 찾음
    // ArtistProfile.user.userId 로 조건을 건다
    Optional<ArtistProfile> findByUser_UserId(Long userId);

    // 해당 유저가 존재 하는가
    boolean existsByUser_UserId(Long userId);

    // admin이 artist 승인 시켜주는 로직

    // 아티스트 전환 요청 리스트
    // JPA가 LAZY로 잡아둔 연관관계를 "이번 조회에서는 같이 가져와라" 고 지정
    // N+1 문제를 줄이려고 쓰는 fetch 전략 오버라이드 도구
    // 서비스에서 여러번 호출해도 추가 쿼리가 안나가게 도와줌
    @EntityGraph(attributePaths = {"user"})
    Page<ArtistProfile> findAllByUser_ArtistStatusOrderBySubmittedDtDesc(
            ArtistStatus status, Pageable pageable
    );

    // 아티스트 프로필 기준으로 찾음(상세)
    // ArtistProfile 가져올때 전체 다 가져와줌
    //ap.getArtistInstruments().stream()
    //  .map(ai -> ai.getInstrument().getCategory()) -> 이런게 돌아도 LAZY 로딩으로 쿼리추가 안나가게 함
    @EntityGraph(attributePaths
            = {"user", "artistInstruments", "artistInstruments.instrument"})
    Optional<ArtistProfile> findWithDetailByArtistProfileId(Long artistProfileId);
}
