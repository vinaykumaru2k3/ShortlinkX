package com.link.shortlinkx.gateway.controller;

import com.link.shortlinkx.exception.ErrorDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final String SERVICE_UNAVAILABLE_MSG =
            "The URL shortening service is temporarily unavailable. Please try again later.";

    @RequestMapping("/shorten")
    public Mono<ResponseEntity<ErrorDetails>> shortenFallback() {
        return buildFallback(SERVICE_UNAVAILABLE_MSG);
    }

    @RequestMapping("/urls")
    public Mono<ResponseEntity<ErrorDetails>> urlsFallback() {
        return buildFallback(SERVICE_UNAVAILABLE_MSG);
    }

    private Mono<ResponseEntity<ErrorDetails>> buildFallback(String message) {
        ErrorDetails error = new ErrorDetails(
                LocalDateTime.now(),
                message,
                "Service unavailable (Circuit Breaker triggered)",
                "FALLBACK"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }
}
