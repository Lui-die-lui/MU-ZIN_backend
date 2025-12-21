package com.muzin.mu.zin.entity.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 공통 시간 컬럼
/*
* 해당 클래스는 테이블로 만들어지지 않음
* 대신 이 클래스를 상속한 엔티티의 테이블 컬럼으로 포함됨
* */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // 자동 채우기 스위치
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "create_dt", nullable = false, updatable = false)
    private LocalDateTime createDt;

    @LastModifiedDate
    @Column(name = "update_dt")
    private LocalDateTime updateDt;

}
