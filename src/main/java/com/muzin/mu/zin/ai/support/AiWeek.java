package com.muzin.mu.zin.ai.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

// 주간·월간 기준 계산 (입력은 KST LocalDateTime = TimeDefaults.nowKst())
// 한 주는 월요일 00:00 KST 에 시작 -> 일요일 23:59:59 까지 같은 주
public final class AiWeek {
    private AiWeek() {}

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public static LocalDate weekStart(LocalDateTime nowKst) {
        return nowKst.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // 다음 초기화 시각 = 다음 주 월요일 00:00 KST
    public static LocalDateTime nextReset(LocalDateTime nowKst) {
        return weekStart(nowKst).plusWeeks(1).atStartOfDay();
    }

    public static String monthKey(LocalDateTime nowKst) {
        return nowKst.format(MONTH);
    }
}
