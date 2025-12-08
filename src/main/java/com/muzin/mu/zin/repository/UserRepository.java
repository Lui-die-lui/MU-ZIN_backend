package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}
