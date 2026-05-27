package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin employee-facing leave endpoints at /api/v1/leaves.
 * <p>
 * The main leave lifecycle lives in LeaveController (/api/v1/hr/leave).
 * This controller exposes the employee self-service paths the Angular
 * employee-dashboard calls directly, specifically the leave balance summary.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class EmployeeLeaveController {

    private static final String EMP_TYPE   = "Employee";
    private static final String LEAVE_TYPE = "LeaveRequest";

    private final GenericEntityService entityService;
    private final ObjectMapper objectMapper;

    /**
     * GET /api/v1/leaves/my/balance
     * Returns the leave balance summary for the currently authenticated employee.
     * Reads ptoBalance / sickBalance / holidayBalance from the Employee payload.
     * Falls back to default values if no Employee record exists yet.
     */
    @GetMapping("/my/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ArrayNode> getMyLeaveBalance(
            @AuthenticationPrincipal UserPrincipal caller) {

        log.debug("GET /api/v1/leaves/my/balance - caller={}", caller.getUsername());

        int ptoBalance     = 0;
        int sickBalance    = 0;
        int holidayBalance = 0;
        int ptoTotal       = 0;
        int sickTotal      = 0;
        int holidayTotal   = 0;

        try {
            EntityData empData = entityService.findById(EMP_TYPE, caller.getUserId(), caller);
            JsonNode p = empData.getPayload();
            ptoBalance     = p.path("ptoBalance").asInt(0);
            sickBalance    = p.path("sickBalance").asInt(0);
            holidayBalance = p.path("holidayBalance").asInt(0);
            ptoTotal       = p.path("ptoTotal").asInt(ptoBalance);
            sickTotal      = p.path("sickTotal").asInt(sickBalance);
            holidayTotal   = p.path("holidayTotal").asInt(holidayBalance);
        } catch (Exception e) {
            log.debug("No Employee record found for {}; returning default balances", caller.getUsername());
        }

        ArrayNode result = objectMapper.createArrayNode();
        result.add(balanceNode("Annual",  ptoTotal  - ptoBalance,     ptoTotal));
        result.add(balanceNode("Sick",    sickTotal - sickBalance,    sickTotal));
        result.add(balanceNode("Holiday", holidayTotal - holidayBalance, holidayTotal));

        return ResponseEntity.ok(result);
    }

    private ObjectNode balanceNode(String type, int used, int total) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("type",  type);
        n.put("used",  used);
        n.put("total", total);
        return n;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<EntityData>> getMyLeaveRequests(
            Pageable pageable, @AuthenticationPrincipal UserPrincipal caller) {

        Page<EntityData> mine = entityService.findByPayloadField(
                LEAVE_TYPE, "employeeId", caller.getUserId().toString(), pageable, caller);
        return ResponseEntity.ok(mine);
    }
}