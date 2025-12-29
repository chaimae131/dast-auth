package com.CAN.auth_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    private String username;

    @NotBlank
    @Size(max = 100)
    @Email
    @Column(unique = true)
    private String email;

    @NotBlank
    @Size(max = 120)
    private String password;

    private boolean enabled; // false until email verification

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    // ===== NOUVELLES COLONNES POUR LE PROFIL =====
    @Size(max = 100)
    @Column(name = "full_name")
    private String fullName;

    @Size(max = 20)
    @Column(name = "phone_number")
    private String phoneNumber;

    @Size(max = 100)
    private String city;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}