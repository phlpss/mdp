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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    // ── Manager ──────────────────────────────────────────────────────────────

    @GetMapping("/manager/kpis")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'SHIFT_SUPERVISOR', 'HR_MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<List<Map<String, Object>>> getManagerKpis(
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/manager/kpis - requester={}", caller.getUsername());
        return ResponseEntity.ok(List.of(
                kpi("Today's Revenue",    "$3,420",  8.2,  "increase", "attach_money",  "#388e3c"),
                kpi("Pending Leaves",     "4",       null, null,       "event_busy",    "#f57c00"),
                kpi("Open Shifts",        "2",       null, null,       "schedule",      "#e53935"),
                kpi("Low Stock Items",    "3",       null, null,       "inventory",     "#7b1fa2"),
                kpi("Staff Present",      "8",       null, null,       "people",        "#1976d2"),
                kpi("Orders Today",       "127",     5.4,  "increase", "receipt_long",  "#0288d1")
        ));
    }

    @GetMapping("/manager/alerts")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'SHIFT_SUPERVISOR', 'HR_MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<List<Map<String, Object>>> getManagerAlerts(
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/manager/alerts - requester={}", caller.getUsername());
        return ResponseEntity.ok(List.of(
                alert("warning", "Low Stock",       "Oat milk below reorder threshold (2 units left)"),
                alert("info",    "Shift Gap",       "No barista scheduled for tomorrow 14:00–18:00"),
                alert("warning", "Leave Pending",   "4 leave requests awaiting your approval"),
                alert("success", "Closing Report",  "Yesterday's closing report submitted successfully")
        ));
    }

    // ── Owner ─────────────────────────────────────────────────────────────────

    @GetMapping("/owner/kpis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getOwnerKpis(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/owner/kpis period={} - requester={}", period, caller.getUsername());
        return ResponseEntity.ok(List.of(
                kpi("Monthly Revenue",   "$42,500", 12.3, "increase", "trending_up",  "#388e3c"),
                kpi("Monthly Expenses",  "$28,200",  4.1, "increase", "trending_down","#e53935"),
                kpi("Net Profit",        "$14,300", 22.4, "increase", "savings",      "#1976d2"),
                kpi("Profit Margin",     "33.6%",    2.8, "increase", "percent",      "#7b1fa2"),
                kpi("Total Employees",   "18",      null, null,       "people",       "#f57c00"),
                kpi("Avg Daily Revenue", "$1,417",  null, null,       "bar_chart",    "#0288d1")
        ));
    }

    @GetMapping("/owner/revenue-chart")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getOwnerRevenueChart(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/owner/revenue-chart period={} - requester={}", period, caller.getUsername());
        var revenue = new java.util.LinkedHashMap<String, Object>();
        revenue.put("label", "Revenue");
        revenue.put("data", List.of(38000,41000,45000,42000,39000,43000,47000,50000,44000,46000,48000,42500));
        revenue.put("borderColor", "#1976d2");
        revenue.put("backgroundColor", "rgba(25,118,210,0.1)");
        revenue.put("fill", true);

        var expenses = new java.util.LinkedHashMap<String, Object>();
        expenses.put("label", "Expenses");
        expenses.put("data", List.of(24000,26000,28000,27000,25000,27000,29000,31000,28000,29000,30000,28200));
        expenses.put("borderColor", "#e53935");
        expenses.put("backgroundColor", "rgba(229,57,53,0.1)");
        expenses.put("fill", true);

        var chart = new java.util.LinkedHashMap<String, Object>();
        chart.put("labels", List.of("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"));
        chart.put("datasets", List.of(revenue, expenses));
        return ResponseEntity.ok(chart);
    }

    @GetMapping("/owner/expense-breakdown")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getOwnerExpenseBreakdown(
            @RequestParam(defaultValue = "month") String period,
            @AuthenticationPrincipal UserPrincipal caller) {
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        log.info("GET /dashboard/owner/expense-breakdown period={} - requester={}", period, caller.getUsername());
        var breakdown = new java.util.LinkedHashMap<String, Object>();
        breakdown.put("label", "Expenses");
        breakdown.put("data", List.of(35, 40, 8, 6, 5, 6));
        breakdown.put("backgroundColor", List.of("#1976d2","#388e3c","#f57c00","#7b1fa2","#0288d1","#78909c"));

        var expChart = new java.util.LinkedHashMap<String, Object>();
        expChart.put("labels", List.of("Food Cost","Labor","Utilities","Supplies","Marketing","Other"));
        expChart.put("datasets", List.of(breakdown));
        return ResponseEntity.ok(expChart);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> kpi(String label, Object value, Double change,
                                    String changeType, String icon, String color) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("label", label);
        m.put("value", value);
        if (change != null)     m.put("change", change);
        if (changeType != null) m.put("changeType", changeType);
        m.put("icon", icon);
        m.put("color", color);
        return m;
    }

    private Map<String, Object> alert(String severity, String title, String message) {
        return Map.of("severity", severity, "title", title, "message", message);
    }
}