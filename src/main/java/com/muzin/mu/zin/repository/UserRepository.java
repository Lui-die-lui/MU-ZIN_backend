package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("""
        select u
        from User u
        left join fetch u.userRoles ur
        left join fetch ur.role r
        where u.userId = :userId
    """)
    Optional<User> findWithRolesByUserId(Integer userId);

    Optional<User> findByEmail(String email);

}
