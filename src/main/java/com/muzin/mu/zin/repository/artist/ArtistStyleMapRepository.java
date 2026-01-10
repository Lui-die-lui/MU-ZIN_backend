package com.muzin.mu.zin.repository.artist;

import com.muzin.mu.zin.entity.artist.ArtistStyleMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistStyleMapRepository extends JpaRepository<ArtistStyleMap, Long> {

    // 해당 아티스트 프로필에 해당 레슨 태그가 존재하는지 - 중복 체크용
    boolean existsByArtistProfile_ArtistProfileIdAndLessonStyleTag_LessonStyleTagId(
            Long artistProfileId, Long lessonStyleTagId
    );

    // 해당 아티스트가 가지고있는 아티스트 스타일 리스트
    List<ArtistStyleMap> findAllByArtistProfile_ArtistProfileId(Long ArtistProfileId);

    // 가지고있는 스타일 수정 - 이거 아티스트 악기랑 같은 상황
    void deleteByArtistProfile_ArtistProfileId(Long artistProfileId);

}
