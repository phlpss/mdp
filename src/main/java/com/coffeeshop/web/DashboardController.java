package com.coffeeshop.web;

import com.coffeeshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard KPI and alert endpoints consumed by the Angular manager/owner dashboards.
 *
 * All endpoints are stubs. TODO: implement by aggregating EntityData records:
 *   - Manager KPIs: today's revenue (Transaction), pending leaves (LeaveRequest),
 *     open shifts (Shift), low-stock items (InventoryItem)
 *   - Manager alerts: derive from same sources, threshold-based
 *   - Owner KPIs: roll up across all stores for the selected period
 *   - Owner charts: aggregate Transaction EntityData grouped by month
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    // ─────────────────────────────────────────────────────────────────────────
    // Manager dashboard
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/dashboard/manager/kpis
     * Returns KPI cards for the store manager dashboard.
     */
    @GetMapping("/manager/kpis")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'SHIFT_SUPERVISOR', 'HR_MANAGER')")
    public ResponseEntity<List<Object>> getManagerKpis(@AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/manager/kpis - requester={}", caller.getUsername());
        return ResponseEntity.ok(List.of());
    }

    /**
     * GET /api/v1/dashboard/manager/alerts
     * Returns operational alerts for the store manager dashboard.
     */
    @GetMapping("/manager/alerts")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'SHIFT_SUPERVISOR', 'HR_MANAGER')")
    public ResponseEntity<List<Object>> getManagerAlerts(@AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/manager/alerts - requester={}", caller.getUsername());
        return ResponseEntity.ok(List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Owner dashboard
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/dashboard/owner/kpis?period=month
     * Returns high-level KPI cards for the business owner dashboard.
     */
    @GetMapping("/owner/kpis")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<List<Object>> getOwnerKpis(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/owner/kpis period={} - requester={}", period, caller.getUsername());
        return ResponseEntity.ok(List.of());
    }

    /**
     * GET /api/v1/dashboard/owner/revenue-chart?period=month
     * Returns monthly revenue vs expense chart data.
     */
    @GetMapping("/owner/revenue-chart")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<Object> getOwnerRevenueChart(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/owner/revenue-chart period={} - requester={}", period, caller.getUsername());
        return ResponseEntity.ok(java.util.Map.of("labels", List.of(), "datasets", List.of()));
    }

    /**
     * GET /api/v1/dashboard/owner/expense-breakdown?period=month
     * Returns expense breakdown by category for the owner dashboard doughnut chart.
     */
    @GetMapping("/owner/expense-breakdown")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<Object> getOwnerExpenseBreakdown(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/owner/expense-breakdown period={} - requester={}", period, caller.getUsername());
        return ResponseEntity.ok(java.util.Map.of("labels", List.of(), "datasets", List.of()));
    }
}
