package com.coffeeshop.web;

import com.coffeeshop.security.JwtTokenProvider;
import com.coffeeshop.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Authentication controller for JWT token generation.
 * <p>
 * Provides the public /api/v1/auth/login endpoint for obtaining JWT tokens.
 * <p>
 * NOTE: This implementation uses hardcoded in-memory users for demonstration.
 * TODO: Replace with EntityData-based user lookup from PostgreSQL in production.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    // TODO: Replace this hardcoded map with EntityData lookup from PostgreSQL
    private static final Map<String, UserCredentials> IN_MEMORY_USERS = Map.ofEntries(
            Map.entry("barista1", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "barista1",
                    Set.of(Role.BARISTA),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            )),
            Map.entry("shift_supervisor", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "shift_supervisor",
                    Set.of(Role.SHIFT_SUPERVISOR),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            )),
            Map.entry("store_manager", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "store_manager",
                    Set.of(Role.STORE_MANAGER),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            )),
            Map.entry("hr_manager", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    "hr_manager",
                    Set.of(Role.HR_MANAGER),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            )),
            Map.entry("accountant", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000005"),
                    "accountant",
                    Set.of(Role.ACCOUNTANT),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            )),
            Map.entry("it_specialist", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000006"),
                    "it_specialist",
                    Set.of(Role.IT_SPECIALIST),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            )),
            Map.entry("business_owner", new UserCredentials(
                    UUID.fromString("00000000-0000-0000-0000-000000000007"),
                    "business_owner",
                    Set.of(Role.BUSINESS_OWNER),
                    UUID.fromString("00000000-0000-0000-0000-100000000001")
            ))
    );
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public AuthController(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Authenticate a user and return a JWT token.
     * <p>
     * Request body: { "username": "...", "password": "..." }
     * <p>
     * For demonstration purposes, all users share the same password "password".
     * TODO: Implement proper password hashing and EntityData-based user lookup.
     *
     * @param body Request body containing username and password
     * @return JWT token in response body with 200 status
     */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody JsonNode body) {
        String username = body.has("username") ? body.get("username").asText() : null;
        String password = body.has("password") ? body.get("password").asText() : null;

        log.info("Login attempt: username={}", username);

        // Validate request
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Login failed: missing username or password");
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "INVALID_CREDENTIALS");
            error.put("message", "Username and password are required");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        // TODO: Replace hardcoded password check with bcrypt comparison
        // TODO: Lookup user from EntityData with type="User" and query by username field
        if (!password.equals("password")) {
            log.warn("Login failed: invalid password for username={}", username);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "INVALID_CREDENTIALS");
            error.put("message", "Invalid username or password");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }

        UserCredentials user = IN_MEMORY_USERS.get(username);
        if (user == null) {
            log.warn("Login failed: user not found: {}", username);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "INVALID_CREDENTIALS");
            error.put("message", "Invalid username or password");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(
                user.userId,
                user.username,
                user.roles.stream().map(Role::getValue).toList(),
                user.storeLocationId
        );

        log.info("Login successful: username={}, roles={}", username, user.roles.size());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("token", token);
        response.put("username", user.username);
        response.put("userId", user.userId.toString());
        response.put("expiresIn", 86400); // 24 hours in seconds
        response.putArray("roles").addAll(
                user.roles.stream().map(Role::getValue)
                        .map(role -> objectMapper.convertValue(role, JsonNode.class)).toList()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Simple holder for user credentials.
     */
    private static class UserCredentials {
        final UUID userId;
        final String username;
        final Set<Role> roles;
        final UUID storeLocationId;

        UserCredentials(UUID userId, String username, Set<Role> roles, UUID storeLocationId) {
            this.userId = userId;
            this.username = username;
            this.roles = roles;
            this.storeLocationId = storeLocationId;
        }
    }
}

