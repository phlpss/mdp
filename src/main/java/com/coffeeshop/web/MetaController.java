package com.coffeeshop.web;

import com.coffeeshop.metadata.MetadataCacheService;
import com.coffeeshop.metadata.MetaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}