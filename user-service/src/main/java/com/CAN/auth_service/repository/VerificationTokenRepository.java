package com.CAN.auth_service.repository;

import com.CAN.auth_service.entity.User;
import com.CAN.auth_service.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    VerificationToken findByToken(String token);
    VerificationToken findByUser(User user);  // ←  méthode utilisable f delete user service
}