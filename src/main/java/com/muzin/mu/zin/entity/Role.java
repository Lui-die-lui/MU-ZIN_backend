package com.muzin.mu.zin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "role",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "role_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가
    @Column(name = "role_id")
    private Integer roleId;

    // "ROLE_USER", "ROLE_ARTIST", "ROLE_ADMIN" ... (UK)
    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    // "일반 유저", "아티스트", "관리자" 등
    @Column(name = "role_name_kor", nullable = false, length = 50)
    private String roleNameKor;

    @CreationTimestamp
    @Column(name = "create_dt", updatable = false)
    private LocalDateTime createDt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private LocalDateTime updateDt;

    // 해당 Role을 가진 UserRole 목록(역방향 - 왜?)
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserRole> userRoles = new ArrayList<>();

    public void addUserRole(UserRole userRole) {
        userRoles.add(userRole);
        userRole.setRole(this);
    }
}
