package com.CAN.auth_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String fullName;
    private String phoneNumber;
    private String city;
    private String profilePictureUrl;
    private boolean enabled;
}
