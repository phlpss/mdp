package com.coffeeshop.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT token provider for generating and validating HS256 signed tokens.
 * <p>
 * Tokens include claims for:
 * - userId: UUID of the authenticated user
 * - username: username of the user
 * - roles: comma-separated list of role names
 * - storeLocationId: UUID of the user's assigned store location (for scoped queries)
 */
@Slf4j
@Component
public class JwtTokenProvider {
    private final String secret;
    private final long expirationMs;
    private final Algorithm algorithm;

    public JwtTokenProvider(
            @Value("${coffeeshop.jwt.secret}") String secret,
            @Value("${coffeeshop.jwt.expiration-ms}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.algorithm = Algorithm.HMAC256(secret);
    }

    /**
     * Generate a JWT token for the given user.
     *
     * @param userId UUID of the user
     * @param username Username of the user
     * @param roles List of role names
     * @param storeLocationId UUID of the user's store location
     * @return Signed JWT token string
     */
    public String generateToken(UUID userId, String username, Collection<String> roles, UUID storeLocationId) {
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + expirationMs);

            JWTCreator.Builder builder = JWT.create()
                    .withIssuedAt(now)
                    .withExpiresAt(expiryDate)
                    .withClaim(SecurityConstants.JWT_CLAIM_USER_ID, userId.toString())
                    .withClaim(SecurityConstants.JWT_CLAIM_USERNAME, username)
                    .withClaim(SecurityConstants.JWT_CLAIM_ROLES, new ArrayList<>(roles));

            if (storeLocationId != null) {
                builder.withClaim(SecurityConstants.JWT_CLAIM_STORE_LOCATION, storeLocationId.toString());
            }

            String token = builder.sign(algorithm);
            log.debug("JWT token generated for user: {} with {} roles", username, roles.size());
            return token;
        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new RuntimeException("JWT token generation failed", e);
        }
    }

    /**
     * Validate and decode a JWT token.
     *
     * @param token The token string to validate
     * @return DecodedJWT object containing claims
     * @throws JWTVerificationException if token is invalid or expired
     */
    public DecodedJWT validateAndDecode(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(algorithm)
                    .build()
                    .verify(token);
            log.debug("JWT token validated successfully");
            return decodedJWT;
        } catch (JWTVerificationException e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extract user ID from a decoded JWT.
     */
    public UUID getUserId(DecodedJWT decodedJWT) {
        return UUID.fromString(decodedJWT.getClaim(SecurityConstants.JWT_CLAIM_USER_ID).asString());
    }

    /**
     * Extract username from a decoded JWT.
     */
    public String getUsername(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim(SecurityConstants.JWT_CLAIM_USERNAME).asString();
    }

    /**
     * Extract roles from a decoded JWT.
     */
    public Set<Role> getRoles(DecodedJWT decodedJWT) {
        List<String> roleStrings = decodedJWT.getClaim(SecurityConstants.JWT_CLAIM_ROLES).asList(String.class);
        return roleStrings.stream()
                .map(roleStr -> {
                    try {
                        return Role.valueOf(roleStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown role in token: {}", roleStr);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Extract store location ID from a decoded JWT.
     */
    public UUID getStoreLocationId(DecodedJWT decodedJWT) {
        String locationId = decodedJWT.getClaim(SecurityConstants.JWT_CLAIM_STORE_LOCATION).asString();
        return locationId != null ? UUID.fromString(locationId) : null;
    }
}

