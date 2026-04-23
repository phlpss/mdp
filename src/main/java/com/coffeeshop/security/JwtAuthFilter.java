package com.coffeeshop.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter that extracts and validates JWT tokens from the Authorization header.
 *
 * For each request:
 * 1. Extract the Bearer token from the Authorization header
 * 2. Validate and decode the token using JwtTokenProvider
 * 3. Extract claims (userId, username, roles, storeLocationId)
 * 4. Create a UserPrincipal and populate the SecurityContext
 *
 * If token extraction or validation fails, the request proceeds unauthenticated
 * (Spring Security will enforce authentication at the endpoint level).
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = extractJwt(request);

            if (jwt != null) {
                var decodedJWT = jwtTokenProvider.validateAndDecode(jwt);
                var userId = jwtTokenProvider.getUserId(decodedJWT);
                var username = jwtTokenProvider.getUsername(decodedJWT);
                var roles = jwtTokenProvider.getRoles(decodedJWT);
                var storeLocationId = jwtTokenProvider.getStoreLocationId(decodedJWT);

                var userPrincipal = new UserPrincipal(userId, username, roles, storeLocationId);
                var authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT authenticated user: {} with {} roles", username, roles.size());
            }
        } catch (JWTVerificationException e) {
            log.warn("JWT verification failed: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing JWT token", e);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Extract the JWT token from the Authorization header.
     * Expected format: "Bearer {token}"
     */
    private String extractJwt(HttpServletRequest request) {
        String authHeader = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
        }

        return null;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

