package com.link.shortlinkx.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.util.List;

/** Verifies the bearer token carried in the credentials and exposes the user id as authentication details. */
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtUtils jwtUtils;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.justOrEmpty(jwtUtils.parse(token))
                .flatMap(claims -> {
                    Long userId = JwtUtils.userIdOf(claims);
                    if (userId == null || claims.getSubject() == null) {
                        return Mono.<Authentication>error(new BadCredentialsException("Invalid JWT claims"));
                    }
                    UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                            claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    auth.setDetails(userId);
                    return Mono.<Authentication>just(auth);
                })
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid or expired JWT")));
    }
}
