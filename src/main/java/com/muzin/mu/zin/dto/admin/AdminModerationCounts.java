package com.muzin.mu.zin.dto.admin;

// 사이드 메뉴 뱃지용 - 처리 안 된 감지/신고 수
public record AdminModerationCounts(long openFlags, long openReports) {
}
