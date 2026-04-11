package com.coffeeshop.web;

import com.coffeeshop.metadata.MetadataCacheService;
import com.coffeeshop.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints for system operations.
 * 
 * Restricted to IT_SPECIALIST role.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final MetadataCacheService metadataCacheService;
    private final ObjectMapper objectMapper;

    public AdminController(MetadataCacheService metadataCacheService, ObjectMapper objectMapper) {
        this.metadataCacheService = metadataCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * Reload metadata schema from Neo4j without restarting the application.
     * 
     * This endpoint allows IT Specialists to hot-reload the in-memory metadata cache
     * after schema changes have been made in the Neo4j metadata graph.
     * 
     * Access: IT_SPECIALIST only
     * 
     * @param caller Authenticated user
     * @return Confirmation response with metadata cache status
     */
    @PostMapping("/metadata/reload")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> reloadMetadata(@AuthenticationPrincipal UserPrincipal caller) {
        log.info("Metadata reload requested by: {}", caller.getUsername());

        try {
            long startTime = System.currentTimeMillis();
            metadataCacheService.reload();
            long durationMs = System.currentTimeMillis() - startTime;

            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "SUCCESS");
            response.put("message", "Metadata cache reloaded successfully");
            response.put("durationMs", durationMs);
            response.put("requestedBy", caller.getUsername());
            
            var types = metadataCacheService.getAllTypes();
            response.put("typesLoaded", types.size());
            response.putArray("typeNames").addAll(types.keySet());

            log.info("Metadata reload completed in {} ms. Loaded {} types", durationMs, types.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Metadata reload failed", e);

            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("status", "FAILED");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("requestedBy", caller.getUsername());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

