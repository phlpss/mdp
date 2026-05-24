package com.coffeeshop.web;

import com.coffeeshop.exception.UnknownEntityTypeException;
import com.coffeeshop.metadata.MetaAttribute;
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
import java.util.stream.StreamSupport;

/**
 * REST controller exposing the in-memory metadata schema to the frontend.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetadataCacheService metadataCacheService;

    public MetaController(MetadataCacheService metadataCacheService) {
        this.metadataCacheService = metadataCacheService;
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    /** GET /api/v1/meta/types */
    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MetaType>> getAllTypes() {
        log.debug("GET /api/v1/meta/types");
        return ResponseEntity.ok(List.copyOf(metadataCacheService.getAllTypes().values()));
    }

    /** GET /api/v1/meta/types/{name} */
    @GetMapping("/types/{name}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MetaType> getType(@PathVariable String name) {
        log.debug("GET /api/v1/meta/types/{}", name);
        return metadataCacheService.findType(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── WRITE — TYPES ────────────────────────────────────────────────────────

    /**
     * POST /api/v1/meta/types
     * Body: { "name": "Supplier", "sensitiveFields": ["bankAccount"] }
     */
    @PostMapping("/types")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> createType(@RequestBody JsonNode body) {
        String name = body.path("name").asString(null);
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "VALIDATION_ERROR", "message", "Field 'name' is required"));
        }
        if (metadataCacheService.hasType(name)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "CONFLICT", "message", "MetaType '" + name + "' already exists"));
        }
        log.info("POST /api/v1/meta/types - creating type '{}'", name);
        MetaType created = metadataCacheService.createType(name, toStringList(body.path("sensitiveFields")));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/v1/meta/types/{name}
     * Body: { "sensitiveFields": ["salary"] }
     */
    @PutMapping("/types/{name}")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> updateType(@PathVariable String name, @RequestBody JsonNode body) {
        log.info("PUT /api/v1/meta/types/{}", name);
        try {
            MetaType updated = metadataCacheService.updateType(name, toStringList(body.path("sensitiveFields")));
            return ResponseEntity.ok(updated);
        } catch (UnknownEntityTypeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "MetaType '" + name + "' not found"));
        }
    }

    // ── WRITE — ATTRIBUTES ───────────────────────────────────────────────────

    /**
     * POST /api/v1/meta/types/{typeName}/attributes
     * Body: { "name": "emergencyContact", "dataType": "STRING", "mandatory": false }
     */
    @PostMapping("/types/{typeName}/attributes")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> addAttribute(@PathVariable String typeName, @RequestBody JsonNode body) {
        String attrName = body.path("name").asString(null);
        if (attrName == null || attrName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "VALIDATION_ERROR", "message", "Field 'name' is required"));
        }
        log.info("POST /api/v1/meta/types/{}/attributes - adding '{}'", typeName, attrName);
        try {
            MetaType updated = metadataCacheService.addAttribute(typeName, parseAttribute(body));
            return ResponseEntity.status(HttpStatus.CREATED).body(updated);
        } catch (UnknownEntityTypeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "MetaType '" + typeName + "' not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "CONFLICT", "message", e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/meta/types/{typeName}/attributes/{attrName}
     * Body: { "dataType": "STRING", "mandatory": true, "min": 2, "max": 100 }
     */
    @PutMapping("/types/{typeName}/attributes/{attrName}")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Object> updateAttribute(
            @PathVariable String typeName,
            @PathVariable String attrName,
            @RequestBody JsonNode body) {
        log.info("PUT /api/v1/meta/types/{}/attributes/{}", typeName, attrName);
        try {
            MetaAttribute attr = new MetaAttribute(
                    attrName,
                    body.path("dataType").asString("STRING"),
                    body.path("mandatory").asBoolean(false),
                    body.path("min").isNull() || body.path("min").isMissingNode() ? null : body.path("min").asInt(),
                    body.path("max").isNull() || body.path("max").isMissingNode() ? null : body.path("max").asInt(),
                    toStringList(body.path("allowedValues"))
            );
            MetaType updated = metadataCacheService.updateAttribute(typeName, attrName, attr);
            return ResponseEntity.ok(updated);
        } catch (UnknownEntityTypeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "MetaType '" + typeName + "' not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/v1/meta/types/{typeName}/attributes/{attrName}
     */
    @DeleteMapping("/types/{typeName}/attributes/{attrName}")
    @PreAuthorize("hasRole('IT_SPECIALIST')")
    public ResponseEntity<Void> deleteAttribute(
            @PathVariable String typeName,
            @PathVariable String attrName) {
        log.info("DELETE /api/v1/meta/types/{}/attributes/{}", typeName, attrName);
        try {
            metadataCacheService.deleteAttribute(typeName, attrName);
            return ResponseEntity.noContent().build();
        } catch (UnknownEntityTypeException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MetaAttribute parseAttribute(JsonNode body) {
        return new MetaAttribute(
                body.path("name").asString(),
                body.path("dataType").asString("STRING"),
                body.path("mandatory").asBoolean(false),
                body.path("min").isNull() || body.path("min").isMissingNode() ? null : body.path("min").asInt(),
                body.path("max").isNull() || body.path("max").isMissingNode() ? null : body.path("max").asInt(),
                toStringList(body.path("allowedValues"))
        );
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return List.of();
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                    .map(JsonNode::asString)
                    .toList();
        }
        return List.of();
    }
}