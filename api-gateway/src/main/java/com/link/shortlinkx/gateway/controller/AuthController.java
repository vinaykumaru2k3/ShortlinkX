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

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@RequestBody @Valid RegisterRequest request) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(request.username())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username is already taken");
            }
            if (userRepository.existsByEmail(request.email())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is already taken");
            }

            User user = User.builder()
                    .username(request.username())
                    .password(passwordEncoder.encode(request.password()))
                    .email(request.email())
                    .createdAt(LocalDateTime.now())
                    .build();

            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(@RequestBody @Valid LoginRequest request) {
        return Mono.fromCallable(() -> {
            User user = userRepository.findByUsername(request.username())
                    .orElse(null);

            if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
            }

            String token = jwtUtils.generateToken(user.getUsername(), user.getId());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getId()));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
