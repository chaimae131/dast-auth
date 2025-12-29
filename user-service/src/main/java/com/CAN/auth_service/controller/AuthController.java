package com.CAN.auth_service.controller;

import com.CAN.auth_service.dto.LoginRequest;
import com.CAN.auth_service.dto.RegisterRequest;
import com.CAN.auth_service.entity.Role;
import com.CAN.auth_service.entity.User;
import com.CAN.auth_service.entity.VerificationToken;
import com.CAN.auth_service.service.EmailService;
import com.CAN.auth_service.service.JwtService;
import com.CAN.auth_service.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/CAN/auth")
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final JwtService jwtService;

    // === REGISTER ===
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        try {
            // Convertir le rôle String → Enum (avec vérification)
            Role role = Role.valueOf(request.getRole().toUpperCase());

            // Créer l’utilisateur avec le rôle choisi
            VerificationToken token = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    role
            );

            // Envoyer l’email de vérification
            emailService.sendVerificationEmail(request.getEmail(), token.getToken());

            return ResponseEntity.ok("The " + role.name().toLowerCase() + " account has been created . Verify your email to activate it.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("invalid Role. Possible values : VISITOR, PROPOSER, ADMIN.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error while registring : " + e.getMessage());
        }
    }


    // === VERIFY EMAIL ===
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam String token) {
        boolean verified = userService.verifyToken(token);
        if (verified) {
            // Redirection vers le frontend
            String redirectUrl = "http://localhost:3000/verified?status=success";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        } else {
            String redirectUrl = "http://localhost:3000/verified?status=failed";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        }
    }

    // === LOGIN ===
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        User user = userService.authenticate(request.getEmail(), request.getPassword());
        if (user == null) {
            return ResponseEntity.badRequest().body("Invalide credentials or inactive account.");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(token);
    }


    // === VALIDATE TOKEN ===
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        // Vérifier la présence du header Authorization
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "valid", false,
                            "error", "Missing or invalid Authorization header"
                    ));
        }

        //  Extraire le token sans le préfixe "Bearer "
        String token = authHeader.substring(7);

        // Vérifier sa validité via JwtService
        boolean isValid = jwtService.isTokenValid(token);

        if (!isValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "valid", false,
                            "error", "Invalid or expired token"
                    ));
        }

        // Extraire les informations (email et rôle)
        Claims claims = jwtService.extractClaims(token);
        String email = claims.getSubject();
        String role = claims.get("role", String.class);

        // Retourner les infos du token
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "email", email,
                "role", role,
                "expiresAt", claims.getExpiration()
        ));
    }
}
