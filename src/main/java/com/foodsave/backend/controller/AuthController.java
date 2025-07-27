package com.foodsave.backend.controller;

import com.foodsave.backend.dto.AuthRequestDTO;
import com.foodsave.backend.dto.AuthResponseDTO;
import com.foodsave.backend.dto.RegisterRequestDTO;
import com.foodsave.backend.dto.UserDTO;
import com.foodsave.backend.service.AuthService;
import com.foodsave.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Login user")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify user email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh JWT token")
    public ResponseEntity<AuthResponseDTO> refreshToken(@RequestHeader("Authorization") String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user information")
    public ResponseEntity<UserDTO> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    @PostMapping("/dev-login")
    @Operation(summary = "Development login - creates temporary manager token")
    public ResponseEntity<AuthResponseDTO> devLogin(@RequestParam(defaultValue = "STORE_MANAGER") String role) {
        // Только для разработки - создает временного пользователя с нужной ролью
        return ResponseEntity.ok(authService.createDevToken(role));
    }
}
