package com.link.shortlinkx.gateway.controller;

import com.link.shortlinkx.exception.ErrorDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/shorten")
    public Mono<ResponseEntity<ErrorDetails>> shortenFallback() {
        ErrorDetails error = new ErrorDetails(
                LocalDateTime.now(),
                "The URL shortening service is temporarily unavailable. Please try again later.",
                "Service unavailable (Circuit Breaker triggered)",
                "FALLBACK"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }
}
