package com.coffeeshop.web;

import com.coffeeshop.exception.IdempotencyConflictException;
import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Generic REST controller for CRUD operations on all entity types.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/entities/{type}")
public class GenericEntityController {
    private final GenericEntityService genericEntityService;

    // Idempotency cache: maps Idempotency-Key -> ResponseEntity
    // Entries are evicted after 24 hours using a ScheduledExecutorService
    private final ConcurrentHashMap<String, CachedResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public GenericEntityController(GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
        startIdempotencyCacheCleanup();
    }

    /**
     * Create a new entity of the given type.
     *
     * Supports idempotency via Idempotency-Key header:
     * - If the same key is provided twice with identical payload, returns the same response
     * - If the same key is provided with different payloads, returns 409 Conflict
     *
     * @param type Entity type name
     * @param payload Entity payload
     * @param idempotencyKey Optional idempotency key (header: Idempotency-Key)
     * @param caller Authenticated user
     * @return Created entity with 201 status
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> create(@PathVariable String type, @RequestBody JsonNode payload, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @AuthenticationPrincipal UserPrincipal caller) {

        log.info("POST /api/v1/entities/{} - user: {}", type, caller.getUsername());

        // Check idempotency cache
        if (idempotencyKey != null) {
            CachedResponse cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) {
                String cachedPayloadStr = cached.payload.toString();
                String currentPayloadStr = payload.toString();

                if (!cachedPayloadStr.equals(currentPayloadStr)) {
                    log.warn("Idempotency conflict for key: {} with different payload", idempotencyKey);
                    throw new IdempotencyConflictException(idempotencyKey, cached.payload, payload);
                }

                log.debug("Returning cached response for idempotency key: {}", idempotencyKey);
                return cached.response;
            }
        }

        // Create entity
        EntityData created = genericEntityService.create(type, payload, caller);
        ResponseEntity<EntityData> response = new ResponseEntity<>(created, HttpStatus.CREATED);

        // Cache the response if idempotency key provided
        if (idempotencyKey != null) {
            idempotencyCache.put(idempotencyKey, new CachedResponse(payload, response));
            log.debug("Cached response for idempotency key: {}", idempotencyKey);
        }

        return response;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<EntityData>> findAll(
            @PathVariable String type,
            @RequestParam Map<String, String> allParams,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal caller) {

        Map<String, String> filters = new HashMap<>(allParams);
        filters.remove("page"); filters.remove("size"); filters.remove("sort");

        Page<EntityData> page = genericEntityService.findAll(type, filters, pageable, caller);
        return ResponseEntity.ok(page);
    }

    /**
     * Retrieve a single entity by ID.
     *
     * @param type Entity type name
     * @param id Entity UUID
     * @param caller Authenticated user
     * @return The entity with 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> findById(@PathVariable String type, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal caller) {

        log.debug("GET /api/v1/entities/{}/{}", type, id);

        EntityData entity = genericEntityService.findById(type, id, caller);
        return ResponseEntity.ok(entity);
    }

    /**
     * Update an entity.
     *
     * @param type Entity type name
     * @param id Entity UUID
     * @param payload Updated entity payload
     * @param caller Authenticated user
     * @return Updated entity with 200 status
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> update(@PathVariable String type, @PathVariable UUID id, @RequestBody JsonNode payload, @AuthenticationPrincipal UserPrincipal caller) {

        log.info("PUT /api/v1/entities/{}/{} - user: {}", type, id, caller.getUsername());

        EntityData updated = genericEntityService.update(type, id, payload, caller);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> patch(@PathVariable String type, @PathVariable UUID id, @RequestBody JsonNode payload, @AuthenticationPrincipal UserPrincipal caller) {
        log.info("PATCH /api/v1/entities/{}/{} - user: {}", type, id, caller.getUsername());
        EntityData existing = genericEntityService.findById(type, id, caller);
        ObjectNode merged = ((ObjectNode) existing.getPayload()).deepCopy();
        merged.setAll((ObjectNode) payload);
        EntityData updated = genericEntityService.update(type, id, merged, caller);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an entity.
     *
     * @param type Entity type name
     * @param id Entity UUID
     * @param caller Authenticated user
     * @return 204 No Content status
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'IT_SPECIALIST')")
    public ResponseEntity<Void> delete(@PathVariable String type, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal caller) {

        log.info("DELETE /api/v1/entities/{}/{} - user: {}", type, id, caller.getUsername());

        genericEntityService.delete(type, id, caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * Start background task to clean up idempotency cache entries older than 24 hours.
     */
    private void startIdempotencyCacheCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long expiryThreshold = 24 * 60 * 60 * 1000; // 24 hours

            idempotencyCache.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > expiryThreshold);

            log.debug("Idempotency cache cleanup completed. Current size: {}", idempotencyCache.size());
        }, 1, 1, TimeUnit.HOURS);
    }

    /**
     * Wrapper class for caching responses with timestamp.
     */
    private static class CachedResponse {
        final JsonNode payload;
        final ResponseEntity<EntityData> response;
        final long timestamp;

        CachedResponse(JsonNode payload, ResponseEntity<EntityData> response) {
            this.payload = payload;
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
    }
}

