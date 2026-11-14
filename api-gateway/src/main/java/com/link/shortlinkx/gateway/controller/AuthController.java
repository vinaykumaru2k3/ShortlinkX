package com.link.shortlinkx.gateway.controller;

import com.link.shortlinkx.gateway.dto.LoginRequest;
import com.link.shortlinkx.gateway.dto.RegisterRequest;
import com.link.shortlinkx.gateway.entity.User;
import com.link.shortlinkx.gateway.repository.UserRepository;
import com.link.shortlinkx.gateway.security.JwtUtils;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** Compared against when the user does not exist, so response time does not reveal valid usernames. */
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> register(@RequestBody @Valid RegisterRequest request) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(request.username())) {
                return conflict("Username already taken", "The username '" + request.username() + "' is already in use");
            }
            if (userRepository.existsByEmail(request.email())) {
                return conflict("Email already registered", "An account with this email already exists");
            }

            User user = User.builder()
                    .username(request.username())
                    .password(passwordEncoder.encode(request.password()))
                    .email(request.email())
                    .createdAt(LocalDateTime.now())
                    .build();

            try {
                userRepository.save(user);
            } catch (DataIntegrityViolationException e) {
                // Lost a race with a concurrent registration; the unique constraints are the source of truth.
                return conflict("Account already exists", "The username or email is already registered");
            }

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

            boolean valid = passwordEncoder.matches(request.password(), user != null ? user.getPassword() : dummyHash)
                    && user != null;
            if (!valid) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Map<String, Object>>body(
                        errorBody("Authentication failed", "Invalid username or password"));
            }

            String token = jwtUtils.generateToken(user.getUsername(), user.getId());
            return ResponseEntity.ok().<Map<String, Object>>body(Map.of(
                    "token", token,
                    "username", user.getUsername(),
                    "userId", user.getId()
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<Map<String, Object>> conflict(String error, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(error, message));
    }

    private Map<String, Object> errorBody(String error, String message) {
        return Map.of(
                "error", error,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }
}
