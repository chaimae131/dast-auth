package com.CAN.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 50)
    private String username;

    @Email
    @Size(max = 100)
    private String email;

    private String role;

    @Size(max = 100)
    private String fullName;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 100)
    private String city;

    private String profilePictureUrl;

    private Boolean enabled;
}