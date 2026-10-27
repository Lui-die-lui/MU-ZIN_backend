package com.muzin.mu.zin.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 신고하기
//  targetType: ARTIST(아티스트 프로필 ID) / CHAT_ROOM(대화방 ID) / REVIEW(리뷰 ID) / INQUIRY(받은 문의 ID)
public record ReportCreateRequest(
        @NotBlank(message = "신고 대상을 선택해주세요.")
        @Pattern(regexp = "ARTIST|CHAT_ROOM|REVIEW|INQUIRY", message = "신고 대상이 올바르지 않습니다.")
        String targetType,

        @NotNull(message = "신고 대상을 선택해주세요.")
        Long targetId,

        @NotBlank(message = "신고 사유를 선택해주세요.")
        @Pattern(regexp = "SPAM|ABUSE|FRAUD|INAPPROPRIATE|ETC", message = "신고 사유가 올바르지 않습니다.")
        String reason,

        @Size(max = 1000, message = "내용은 1000자 이하로 입력해주세요.")
        String detail
) {
}
