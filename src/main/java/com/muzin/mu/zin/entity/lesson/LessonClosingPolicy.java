package com.muzin.mu.zin.entity.lesson;

public enum LessonClosingPolicy {
    AUTO_CLOSE_WHEN_NO_SLOT, // 타임슬롯 없을때 자동 닫기
    KEEP_OPEN_FOR_REQUEST // 타임슬롯 없어도 요청으로 받을 수 있도록 레슨 열어둠
}
