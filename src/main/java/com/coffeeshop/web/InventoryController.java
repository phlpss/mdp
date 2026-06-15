package com.coffeeshop.web;

import com.coffeeshop.operational.EntityDataRepository;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final EntityDataRepository entityDataRepository;

    /**
     * GET /api/v1/inventory/low-stock-count
     * Returns the count of InventoryItem records where payload.quantity <= payload.reorderLevel.
     */
    @GetMapping("/low-stock-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> getLowStockCount(@AuthenticationPrincipal UserPrincipal caller) {
        log.info("GET /inventory/low-stock-count - requester={}", caller.getUsername());
        int count = entityDataRepository.countWhereQuantityAtOrBelowReorder("InventoryItem", "quantity", "reorderLevel");
        return ResponseEntity.ok(Map.of("count", count));
    }
}