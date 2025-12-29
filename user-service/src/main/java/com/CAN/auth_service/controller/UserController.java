package com.CAN.auth_service.controller;

import com.CAN.auth_service.dto.UpdateProfileRequest;
import com.CAN.auth_service.dto.UpdateUserRequest;
import com.CAN.auth_service.dto.UserProfileDTO;
import com.CAN.auth_service.entity.Role;
import com.CAN.auth_service.entity.User;
import com.CAN.auth_service.service.JwtService;
import com.CAN.auth_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/CAN/users")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    // === GET CURRENT USER PROFILE ===
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        try {
            String email = getCurrentUserEmail();
            UserProfileDTO profile = userService.getUserProfile(email);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: " + e.getMessage()));
        }
    }

    // === UPDATE CURRENT USER PROFILE ===
    @PutMapping("/profile")
    public ResponseEntity<?> updateCurrentUserProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            String email = getCurrentUserEmail();
            UserProfileDTO updatedProfile = userService.updateUserProfile(email, request);
            return ResponseEntity.ok(updatedProfile);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Error updating profile: " + e.getMessage()));
        }
    }

    // === GET ALL USERS (ADMIN ONLY) ===
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            if (!isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. Admin role required."));
            }
            List<UserProfileDTO> users = userService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching users: " + e.getMessage()));
        }
    }

    // === GET USER BY ID (ADMIN ONLY) ===
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            if (!isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. Admin role required."));
            }
            UserProfileDTO user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found: " + e.getMessage()));
        }
    }

    // === UPDATE USER BY ID (ADMIN ONLY) ===
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRequest request) {
        try {
            if (!isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. Admin role required."));
            }
            UserProfileDTO updatedUser = userService.updateUser(id, request);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Error updating user: " + e.getMessage()));
        }
    }

    // === DELETE USER (ADMIN ONLY) ===
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            if (!isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. Admin role required."));
            }
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Error deleting user: " + e.getMessage()));
        }
    }

    // === GET USERS BY ROLE (ADMIN ONLY) ===
    @GetMapping("/role/{role}")
    public ResponseEntity<?> getUsersByRole(@PathVariable String role) {
        try {
            if (!isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. Admin role required."));
            }
            Role userRole = Role.valueOf(role.toUpperCase());
            List<UserProfileDTO> users = userService.getUsersByRole(userRole);
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid role. Possible values: VISITOR, PROPOSER, ADMIN."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching users: " + e.getMessage()));
        }
    }

    // === HELPER METHODS ===
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN"));
    }
}