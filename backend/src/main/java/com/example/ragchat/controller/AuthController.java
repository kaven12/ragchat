package com.example.ragchat.controller;

import com.example.ragchat.dto.request.LoginRequest;
import com.example.ragchat.dto.request.RefreshRequest;
import com.example.ragchat.dto.response.LoginResponse;
import com.example.ragchat.dto.response.RefreshResponse;
import com.example.ragchat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        LoginResponse response = userService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String accessToken = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(RefreshResponse.of(accessToken, 7200000L));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            userService.logout(auth.getName());
        }
        return ResponseEntity.ok().build();
    }
}
