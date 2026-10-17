package com.muzin.mu.zin.policy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

// 정책이 계속 커져서 서비스에 넣는것보다 그냥 유틸로 하나 만듦
// 변경 가능성 있음.
public class TimeSlotPolicy {
    private TimeSlotPolicy() {} // 해당 클래스는 정책용이라고 명시

    public static final int MAX_DAYS_AHEAD = 90;
    // 한 번에 만들 수 있는 시간 개수 (짧은 수업 + 긴 기간이면 수천 개가 한 번에 생길 수 있어서 제한)
    public static final int MAX_SLOTS_PER_REQUEST = 500;


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

    /**
     * 같은 레슨 안에서 시간이 겹치는지 확인 (한 시간 = 시작 ~ 시작 + 수업시간)
     * @param newStarts 새로 만들 시작 시각들
     * @param existingStarts 이미 있는 시작 시각들 (닫힌 시간 포함 - 다시 열 수 있으므로)
     * @return 겹치는 두 시각 (없으면 null) - 하나는 반드시 새 시각
     */
    public static LocalDateTime[] findOverlap(List<LocalDateTime> newStarts, List<LocalDateTime> existingStarts, int durationMin) {
        java.util.TreeMap<LocalDateTime, Boolean> all = new java.util.TreeMap<>(); // 시각 -> 새 시각 여부
        existingStarts.forEach(s -> all.put(s, false));
        newStarts.forEach(s -> all.put(s, true));

        Map.Entry<LocalDateTime, Boolean> prev = null;
        for (Map.Entry<LocalDateTime, Boolean> cur : all.entrySet()) {
            if (prev != null
                    && (prev.getValue() || cur.getValue())
                    && prev.getKey().plusMinutes(durationMin).isAfter(cur.getKey())) {
                return new LocalDateTime[]{prev.getKey(), cur.getKey()};
            }
            prev = cur;
        }
        return null;
    }

    /** 수업시간을 바꿨을 때 기존 시간들끼리 겹치는지 (겹치면 앞쪽 두 시각 반환, 없으면 null) */
    public static LocalDateTime[] findOverlapAmong(List<LocalDateTime> starts, int durationMin) {
        List<LocalDateTime> sorted = starts.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).plusMinutes(durationMin).isAfter(sorted.get(i))) {
                return new LocalDateTime[]{sorted.get(i - 1), sorted.get(i)};
            }
        }
        return null;
    }

    public static String format(LocalDateTime dt) {
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm"));
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
