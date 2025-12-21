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
