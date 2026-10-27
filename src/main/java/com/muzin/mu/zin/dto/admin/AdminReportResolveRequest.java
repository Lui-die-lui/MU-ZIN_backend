package com.muzin.mu.zin.dto.admin;

import jakarta.validation.constraints.Size;

// 신고 처리 (memo 는 선택)
public record AdminReportResolveRequest(
        @Size(max = 255, message = "메모는 255자 이하로 입력해주세요.")
        String memo
) {
}
