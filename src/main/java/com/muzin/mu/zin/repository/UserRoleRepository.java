package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // user가 userRole을 가지고 있는지 검증
    boolean existsByUser_UserId(Long userId);

}
