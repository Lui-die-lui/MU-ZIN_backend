package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
        select u
        from User u
        left join fetch u.userRoles ur
        left join fetch ur.role r
        where u.userId = :userId
    """)
    Optional<User> findWithRolesByUserId(Long userId);

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(Long userId);

    boolean existsByEmail(String email);

    // 이메일 중복 판단은 대소문자 구분 없이 (Foo@x.com / foo@x.com 중복 가입 방지)
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);
}
