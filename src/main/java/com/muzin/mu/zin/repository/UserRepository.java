package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
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

    // 관리자 회원 목록: 이메일/이름 부분 일치 (keyword 는 소문자, 빈 문자열이면 전체) + 정지된 계정만 보기
    @Query("""
        select u
        from User u
        where u.withdrawnDt is null
          and (lower(u.email) like concat('%', :keyword, '%') or lower(u.username) like concat('%', :keyword, '%'))
          and (:suspendedOnly = false or u.suspendedDt is not null)
        order by u.userId desc
    """)
    Page<User> searchForAdmin(String keyword, boolean suspendedOnly, Pageable pageable);

    // 목록에 관리자 표시용 (한 페이지 분량을 한 번에)
    @Query("""
        select ur.user.userId
        from UserRole ur
        where ur.role.roleName = 'ROLE_ADMIN' and ur.user.userId in :userIds
    """)
    List<Long> findAdminUserIds(Collection<Long> userIds);
}
