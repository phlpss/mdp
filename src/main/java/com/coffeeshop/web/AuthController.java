package com.coffeeshop.web;

import com.coffeeshop.operational.EntityDataRepository;
import com.coffeeshop.security.JwtTokenProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.coffeeshop.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    private static final String USER_TYPE = "User";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final EntityDataRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper, EntityDataRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
    public ResponseEntity<Object> login(@RequestBody JsonNode body) { // OK in positive scenario
        String username = body.has("username") ? body.get("username").asString() : null;
        String password = body.has("password") ? body.get("password").asString() : null;

        log.info("Login attempt: username={}", username);

        // Validate request
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Login failed: missing username or password");
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "INVALID_CREDENTIALS");
            error.put("message", "Username and password are required");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        var page = userRepository.findByTypeAndPayloadField(USER_TYPE, "username", username, PageRequest.of(0, 1));
        if (page.isEmpty()) {
            log.warn("Login failed: user not found: {}", username);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "INVALID_CREDENTIALS");
            error.put("message", "Invalid username or password");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }

        var userEntity = page.getContent().getFirst();
        JsonNode payload = userEntity.getPayload();
        String storedHash = payload.path("passwordHash").asString("");

        if (!passwordEncoder.matches(password, storedHash)) {
            log.warn("Login failed: invalid password for username={}", username);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "INVALID_CREDENTIALS");
            error.put("message", "Invalid username or password");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }

        if (!payload.path("isActive").asBoolean(true)) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "ACCOUNT_DISABLED");
            error.put("message", "Account is disabled");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }

        List<String> roles = StreamSupport.stream(payload.path("roles").spliterator(), false).map(JsonNode::asString).collect(Collectors.toList());
        String locationIdStr = payload.path("storeLocationId").asString(null);
        UUID storeLocationId = locationIdStr != null ? UUID.fromString(locationIdStr) : null;

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(userEntity.getId(), username, roles, storeLocationId);

        log.info("Login successful: username={}, roles={}", username, roles);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("token", token);
        response.put("username", username);
        response.put("userId", userEntity.getId().toString());
        response.put("expiresIn", 86400); // 24 hours in seconds
        var rolesArray = response.putArray("roles");
        roles.forEach(rolesArray::add);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     * JWT is stateless; actual token invalidation is client-side.
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal caller) {
        log.debug("Logout: {}", caller.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/auth/refresh
     * Refresh tokens are not yet issued by this backend.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(@RequestBody JsonNode body) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("error", "NOT_IMPLEMENTED");
        error.put("message", "Token refresh not supported; please log in again");
        return new ResponseEntity<>(error, HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * GET /api/v1/auth/me
     * Returns the currently authenticated user's profile derived from the JWT claims.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> getCurrentUser(@AuthenticationPrincipal UserPrincipal caller) {
        log.info("GET /auth/me - caller={}", caller.getUsername());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id",       caller.getUserId().toString());
        response.put("username", caller.getUsername());
        response.put("email",    caller.getUsername() + "@coffeeshop.local");
        response.put("firstName", caller.getUsername());
        response.put("lastName",  "");
        response.put("isActive",  true);
        if (caller.getStoreLocationId() != null) {
            response.put("locationId", caller.getStoreLocationId().toString());
        } else {
            response.putNull("locationId");
        }
        var rolesArray = response.putArray("roles");
        caller.getRoles().forEach(r -> rolesArray.add(r.getValue()));

        return ResponseEntity.ok(response);
    }
}