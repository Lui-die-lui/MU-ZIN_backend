package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // user가 userRole을 가지고 있는지 검증
    boolean existsByUser_UserId(Long userId);

    // 관리자 전체 (운영자 문의 알림 받는 사람). 정지·탈퇴한 관리자는 제외
    @Query("""
            select ur.user.userId from UserRole ur
            where ur.role.roleName = 'ROLE_ADMIN' and ur.user.suspendedDt is null and ur.user.withdrawnDt is null
            """)
    List<Long> findActiveAdminUserIds();

}
