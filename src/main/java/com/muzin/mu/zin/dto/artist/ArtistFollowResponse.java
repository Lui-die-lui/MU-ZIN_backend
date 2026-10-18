package com.muzin.mu.zin.dto.artist;

// 팔로우 상태 (인스타처럼 숫자만 공개, 누가 누구를 팔로우하는지 명단은 없음)
public record ArtistFollowResponse(
        Long artistProfileId,
        long followerCount, // 이 아티스트를 팔로우하는 수
        boolean following   // 내가 팔로우 중인지 (비로그인 false)
) {
}
