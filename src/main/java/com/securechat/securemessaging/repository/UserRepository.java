package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Unverified account lookup
    Optional<User> findByUsernameAndEmailVerifiedFalse(String username);

    Optional<User> findByEmailAndEmailVerifiedFalse(String email);
}