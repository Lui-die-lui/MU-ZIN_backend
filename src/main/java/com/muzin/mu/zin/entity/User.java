package com.muzin.mu.zin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "username")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가
    @Column(name = "user_id")
    private Long userId;

    // 이메일 - username 과 동일(UK)
    @Column(nullable = false, length = 100)
    private String email;

    // 비밀번호 - 응답에서 숨김
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    // username: email 받음 - 변경 가능(UK)
    @Column(nullable = false, length = 100)
    private String username;

    // 프로필 이미지
    @Column(name = "profile_img_url")
    private String profileImgUrl;

    // 이메일 인증 완료 여부
    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    // 아티스트 전환 상태 Enum
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "artist_status", nullable = false, length = 20)
    private ArtistStatus artistStatus = ArtistStatus.NONE;


    // 샘플(시연용) 계정 - 추천 화면에서 운영은 제외, 개발은 "샘플"로 표시 (app.sample-data.show)
    @Builder.Default
    @Column(name = "is_sample", nullable = false)
    private boolean sample = false;

    // 계정 정지 시각 (null 이면 정상) - 정지되면 유효한 토큰이 있어도 모든 인증 API 차단
    @Column(name = "suspended_dt")
    private LocalDateTime suspendedDt;

    @Column(name = "suspended_reason")
    private String suspendedReason;

    @Column(name = "withdrawn_dt")
    private LocalDateTime withdrawnDt;

    @Builder.Default
    @Column(name = "ever_suspended", nullable = false)
    private boolean everSuspended = false;

    public boolean isSuspended() {
        return suspendedDt != null;
    }

    public boolean isWithdrawn() {
        return withdrawnDt != null;
    }

    public String getDisplayName() {
        return isWithdrawn() ? "탈퇴한 회원" : username;
    }

    public void suspend(LocalDateTime at, String reason) {
        this.suspendedDt = at;
        this.suspendedReason = reason;
        this.everSuspended = true;
    }

    public void unsuspend() {
        this.suspendedDt = null;
        this.suspendedReason = null;
    }

    // 해당 유저의 UserRole 리스트
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserRole> userRoles = new ArrayList<>();

    // 편의 메서드 (양방향 연관관계 세팅 - 이게 뭐임?)
    public void addUserRole(UserRole userRole) {
        userRoles.add(userRole);
        userRole.setUser(this);
    }

    // User에만 편의메서드 두고
//    public UserRole addRole(Role role) {
//        UserRole userRole = UserRole.builder()
//                .user(this)
//                .role(role)
//                .build();
//
//        userRoles.add(userRole);
//        role.getUserRoles().add(userRole);
//        return userRole;
//    }

}
