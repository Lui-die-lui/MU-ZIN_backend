package com.muzin.mu.zin.policy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// 정책이 계속 커져서 서비스에 넣는것보다 그냥 유틸로 하나 만듦
// 변경 가능성 있음.
public class TimeSlotPolicy {
    private TimeSlotPolicy() {} // 해당 클래스는 정책용이라고 명시

    public static final int MAX_DAYS_AHEAD = 90;


    // 허용 최대 시각 (상한, exclusive): TimeDefault 오늘 + 91일 00:00
    public static LocalDateTime upperExclusive(LocalDateTime nowKst) {
        return nowKst.toLocalDate()
                .plusDays(MAX_DAYS_AHEAD + 1)
                .atStartOfDay();
    }

    public static void validateStartDt(LocalDateTime startDt, LocalDateTime nowKst) {
        if (startDt.isBefore(nowKst)) {
            throw new IllegalArgumentException("과거 시간의 타임슬롯은 생성할 수 없습니다.");
        }
        if (!startDt.isBefore(upperExclusive(nowKst))) {
            throw new IllegalArgumentException("타임슬롯은 오늘 기준 앞으로 90일 이내만 생성 가능합니다.");
        }
    }

    public static void validateStartDts(List<LocalDateTime> startDts, LocalDateTime nowKst) {
        for (LocalDateTime dt : startDts) validateStartDt(dt, nowKst);
    }

    public static void validateQueryRange(LocalDateTime from, LocalDateTime to, LocalDateTime nowKst) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("종료일은 시작일보다 앞이어야 합니다.");
        }

        if (to.isAfter(upperExclusive(nowKst))) {
            throw new IllegalArgumentException("조회 범위는 오늘기준 앞으로 90일 이내로만 가능합니다.");
        }
    }
}
