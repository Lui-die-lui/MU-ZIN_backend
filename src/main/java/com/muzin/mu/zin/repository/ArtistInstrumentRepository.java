package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.ArtistInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistInstrumentRepository extends JpaRepository<ArtistInstrument, Long> {

    // 악기 추가할 때 중복 추가 방지
    boolean existsByArtistProfile_ArtistProfileIdAndInstrument_InstId(Long artistProfileId, Long instId);

    // 해당 아티스트가 가능한 악기 목록 or 수정 화면에서 체크된 악기들 미리 표시할 때
    List<ArtistInstrument> findAllByArtistProfile_ArtistProfileId(Long artistProfileId);

    // 아티스트가 선택한 악기를 체크 해제했을 때(단건)
    void deleteByArtistProfile_ArtistProfileIdAndInstrument_InstId(Long artistProfileId, Long instId);

    // 해당 아티스트 프로필이 가진 악기 매핑을 전부 삭제
    // 아티스트가 선택한 악기 목록을 최종 선택 리스트로 한 번에 맞춰 저장할 때
    // 기존 매핑 삭제 후 최종 리스트 다시저장...? 왜? 하나씩 지우고 더하기 불가능?
    void deleteByArtistProfile_ArtistProfileId(Long artistProfileId);

    // flush 쓰거나 이거 쓰거나
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ArtistInstrument ai where ai.artistProfile.artistProfileId = :profileId")
    void deleteAllByProfileId(@Param("profileId") Long profileId);
}
