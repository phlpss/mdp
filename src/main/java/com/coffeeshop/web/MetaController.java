package com.coffeeshop.web;

import com.coffeeshop.metadata.MetadataCacheService;
import com.coffeeshop.metadata.MetaType;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the in-memory metadata schema to the frontend.
 * <p>
 * These endpoints are read-only and serve the MetaType definitions that
 * the Angular Metadata Studio uses to render dynamic forms and entity tables.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetadataCacheService metadataCacheService;

    public MetaController(MetadataCacheService metadataCacheService) {
        this.metadataCacheService = metadataCacheService;
    }

    /**
     * GET /api/v1/meta/types
     * Returns all MetaType definitions from the in-memory cache.
     */
    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MetaType>> getAllTypes() {
        log.debug("GET /api/v1/meta/types");
        List<MetaType> types = List.copyOf(metadataCacheService.getAllTypes().values());
        return ResponseEntity.ok(types);
    }

    /**
     * GET /api/v1/meta/types/{name}
     * Returns a single MetaType by name.
     */
    @GetMapping("/types/{name}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MetaType> getType(@PathVariable String name) {
        log.debug("GET /api/v1/meta/types/{}", name);
        return metadataCacheService.findType(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/v1/meta/types
     * Create a new MetaType in Neo4j.
     * TODO: Implement write-through to Neo4j and reload cache.
     */
    @PostMapping("/types")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> createType(@RequestBody JsonNode body) {
        log.warn("POST /api/v1/meta/types - not yet implemented");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "NOT_IMPLEMENTED", "message", "MetaType write-through to Neo4j pending"));
    }

    /**
     * PUT /api/v1/meta/types/{name}
     * Update an existing MetaType in Neo4j.
     * TODO: Implement write-through to Neo4j and reload cache.
     */
    @PutMapping("/types/{name}")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> updateType(@PathVariable String name, @RequestBody JsonNode body) {
        log.warn("PUT /api/v1/meta/types/{} - not yet implemented", name);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "NOT_IMPLEMENTED", "message", "MetaType write-through to Neo4j pending"));
    }

    /**
     * POST /api/v1/meta/types/{typeName}/attributes
     * Add an attribute to an existing MetaType.
     * TODO: Implement write-through to Neo4j and reload cache.
     */
    @PostMapping("/types/{typeName}/attributes")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> addAttribute(@PathVariable String typeName, @RequestBody JsonNode body) {
        log.warn("POST /api/v1/meta/types/{}/attributes - not yet implemented", typeName);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "NOT_IMPLEMENTED", "message", "MetaAttribute write-through to Neo4j pending"));
    }

    /**
     * PUT /api/v1/meta/types/{typeName}/attributes/{attrName}
     * Update an existing attribute.
     * TODO: Implement write-through to Neo4j and reload cache.
     */
    @PutMapping("/types/{typeName}/attributes/{attrName}")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> updateAttribute(
            @PathVariable String typeName, @PathVariable String attrName, @RequestBody JsonNode body) {
        log.warn("PUT /api/v1/meta/types/{}/attributes/{} - not yet implemented", typeName, attrName);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "NOT_IMPLEMENTED", "message", "MetaAttribute write-through to Neo4j pending"));
    }

    /**
     * DELETE /api/v1/meta/types/{typeName}/attributes/{attrName}
     * Remove an attribute from a MetaType.
     * TODO: Implement write-through to Neo4j and reload cache.
     */
    @DeleteMapping("/types/{typeName}/attributes/{attrName}")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Void> deleteAttribute(@PathVariable String typeName, @PathVariable String attrName) {
        log.warn("DELETE /api/v1/meta/types/{}/attributes/{} - not yet implemented", typeName, attrName);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}