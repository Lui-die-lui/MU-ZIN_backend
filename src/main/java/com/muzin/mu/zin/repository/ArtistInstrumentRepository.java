package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.ArtistInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistInstrumentRepository extends JpaRepository<ArtistInstrument, Long> {

    // 악기 추가할 때 중복 추가 방지
    boolean existsByArtistProfile_ArtistProfileIdAndInstrument_InstId(Long artistProfileId, Long instId);

    // 해당 아티스트가 가능한 악기 목록 or 수정 화면에서 체크된 악기들 미리 표시할 때
    List<ArtistInstrument> findAllByArtistProfile_ArtistProfileId(Long artistProfileId);

    // 아티스트가 선택한 악기를 체크 해제했을 때
    void deleteByArtistProfile_ArtistProfileIdAndInstrument_InstId(Long artistProfileId, Long instId);
}
