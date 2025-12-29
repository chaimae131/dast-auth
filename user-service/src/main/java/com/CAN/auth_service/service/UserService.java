package com.CAN.auth_service.service;

import com.CAN.auth_service.dto.UpdateProfileRequest;
import com.CAN.auth_service.dto.UpdateUserRequest;
import com.CAN.auth_service.dto.UserProfileDTO;
import com.CAN.auth_service.entity.Role;
import com.CAN.auth_service.entity.User;
import com.CAN.auth_service.entity.VerificationToken;
import com.CAN.auth_service.repository.UserRepository;
import com.CAN.auth_service.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    // === REGISTER ===
    public VerificationToken registerUser(String username, String email, String rawPassword, Role role) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .enabled(false)
                .role(role)
                .build();

        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .build();

        tokenRepository.save(verificationToken);
        return verificationToken;
    }

    // === EMAIL VERIFICATION ===
    public boolean verifyToken(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token);
        if (verificationToken == null || verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return false;
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        return true;
    }

    // === LOGIN ===
    public User authenticate(String email, String rawPassword) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) return null;

        User user = optionalUser.get();
        if (!user.isEnabled()) return null;

        boolean matches = passwordEncoder.matches(rawPassword, user.getPassword());
        return matches ? user : null;
    }

    // === GET USER PROFILE ===
    public UserProfileDTO getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDTO(user);
    }

    // === UPDATE USER PROFILE ===
    @Transactional
    public UserProfileDTO updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        userRepository.save(user);
        return convertToDTO(user);
    }

    // === GET ALL USERS (ADMIN) ===
    public List<UserProfileDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // === GET USER BY ID (ADMIN) ===
    public UserProfileDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return convertToDTO(user);
    }

    // === UPDATE USER (ADMIN) ===
    @Transactional
    public UserProfileDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.findByEmail(request.getUsername()).isPresent()) {
                throw new RuntimeException("Username already taken");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getRole() != null) {
            try {
                Role role = Role.valueOf(request.getRole().toUpperCase());
                user.setRole(role);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role");
            }
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        userRepository.save(user);
        return convertToDTO(user);
    }

    // === DELETE USER (ADMIN) ===
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }

        // on Supprime d'abord le token de vérification associé(kayna relation one to one entre les 2)
        User user = userRepository.findById(id).get();
        VerificationToken token = tokenRepository.findByUser(user);
        if (token != null) {
            tokenRepository.delete(token);
        }

        // Ensuite on supprime user
        userRepository.deleteById(id);
    }

    // === GET USERS BY ROLE ===
    public List<UserProfileDTO> getUsersByRole(Role role) {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == role)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // === HELPER: Convert User to DTO ===
    private UserProfileDTO convertToDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .city(user.getCity())
                .profilePictureUrl(user.getProfilePictureUrl())
                .enabled(user.isEnabled())
                .build();
    }
}