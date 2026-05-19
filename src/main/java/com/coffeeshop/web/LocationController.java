package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for store location lookups.
 * <p>
 * Locations are stored as EntityData with type="StoreLocation".
 * The topbar uses this endpoint to populate the location selector.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private static final String LOCATION_TYPE = "StoreLocation";

    private final GenericEntityService genericEntityService;

    public LocationController(GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
    }

    /**
     * GET /api/v1/locations
     * Returns all store locations (up to 100).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EntityData>> getAllLocations(
            @AuthenticationPrincipal UserPrincipal caller) {

        log.debug("GET /api/v1/locations - caller={}", caller.getUsername());

        List<EntityData> locations = genericEntityService
                .findAll(LOCATION_TYPE, PageRequest.of(0, 100), caller)
                .getContent();

        return ResponseEntity.ok(locations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityData> getUserLocation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal caller) {
        Page<EntityData> location = genericEntityService.findByPayloadField(
                LOCATION_TYPE, "employeeId", id.toString(), null, caller);
        return ResponseEntity.ok(location.getContent().getFirst());
    }
}