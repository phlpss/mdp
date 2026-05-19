package com.coffeeshop.web;

import com.coffeeshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Inventory-related endpoints.
 *
 * Thin endpoints on top of the generic InventoryItem EntityData.
 * TODO: implement by querying InventoryItem EntityData where quantity <= reorderLevel.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    /**
     * GET /api/v1/inventory/low-stock-count
     * Returns the count of InventoryItem records where quantity is at or below reorderLevel.
     */
    @GetMapping("/low-stock-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> getLowStockCount(@AuthenticationPrincipal UserPrincipal caller) {
        log.info("GET /inventory/low-stock-count - requester={}", caller.getUsername());
        // TODO: query InventoryItem EntityData where payload.quantity <= payload.reorderLevel
        return ResponseEntity.ok(Map.of("count", 0));
    }
}
