package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Find a specific unverified account (so we can overwrite it on re-registration)
    java.util.Optional<User> findByUsernameAndEmailVerifiedFalse(String username);

    java.util.Optional<User> findByEmailAndEmailVerifiedFalse(String email);
}
