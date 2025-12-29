package com.CAN.auth_service.repository;

import com.CAN.auth_service.entity.User;
import com.CAN.auth_service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByRole(Role role);
}
