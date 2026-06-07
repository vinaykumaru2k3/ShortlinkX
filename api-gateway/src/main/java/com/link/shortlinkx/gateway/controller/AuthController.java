package com.link.shortlinkx.gateway.controller;

import com.link.shortlinkx.gateway.dto.AuthResponse;
import com.link.shortlinkx.gateway.dto.LoginRequest;
import com.link.shortlinkx.gateway.dto.RegisterRequest;
import com.link.shortlinkx.gateway.entity.User;
import com.link.shortlinkx.gateway.repository.UserRepository;
import com.link.shortlinkx.gateway.security.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> register(@RequestBody @Valid RegisterRequest request) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(request.username())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).<Map<String, Object>>body(
                        errorBody("Username already taken", "The username '" + request.username() + "' is already in use"));
            }
            if (userRepository.existsByEmail(request.email())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).<Map<String, Object>>body(
                        errorBody("Email already registered", "An account with this email already exists"));
            }

            User user = User.builder()
                    .username(request.username())
                    .password(passwordEncoder.encode(request.password()))
                    .email(request.email())
                    .createdAt(LocalDateTime.now())
                    .build();

            userRepository.save(user);

            return ResponseEntity.status(HttpStatus.CREATED).<Map<String, Object>>body(Map.of(
                    "message", "Registration successful",
                    "username", user.getUsername()
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@RequestBody @Valid LoginRequest request) {
        return Mono.fromCallable(() -> {
            User user = userRepository.findByUsername(request.username()).orElse(null);

            if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Map<String, Object>>body(
                        errorBody("Authentication failed", "Invalid username or password"));
            }

            String token = jwtUtils.generateToken(user.getUsername(), user.getId());
            AuthResponse authResponse = new AuthResponse(token, user.getUsername(), user.getId());

            return ResponseEntity.ok().<Map<String, Object>>body(Map.of(
                    "token", authResponse.token(),
                    "username", authResponse.username(),
                    "userId", authResponse.userId()
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> errorBody(String error, String message) {
        return Map.of(
                "error", error,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }
}
