package com.coffeeshop.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwt(request);

            if (jwt != null) {
                DecodedJWT decodedJWT = jwtTokenProvider.validateAndDecode(jwt);
                UUID userId = jwtTokenProvider.getUserId(decodedJWT);
                String username = jwtTokenProvider.getUsername(decodedJWT);
                var roles = jwtTokenProvider.getRoles(decodedJWT);
                UUID storeLocationId = jwtTokenProvider.getStoreLocationId(decodedJWT);

                UserPrincipal userPrincipal = new UserPrincipal(userId, username, roles, storeLocationId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userPrincipal, null,
                                userPrincipal.getAuthorities());
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
     * Extract JWT token from Authorization header.
     * Expected format: "Bearer {token}"
     */
    private String extractJwt(HttpServletRequest request) {
        String authHeader = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
        }

        return null;
    }
}

