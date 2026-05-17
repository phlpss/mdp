package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
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
public class EmployeeLeaveController {

    private static final String EMP_TYPE = "Employee";

    private final GenericEntityService genericEntityService;
    private final ObjectMapper objectMapper;

    public EmployeeLeaveController(GenericEntityService genericEntityService,
                                   ObjectMapper objectMapper) {
        this.genericEntityService = genericEntityService;
        this.objectMapper = objectMapper;
    }

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

        int ptoTotal     = 20;
        int sickTotal    = 10;
        int holidayTotal = 5;

        int ptoUsed = 0, sickUsed = 0, holidayUsed = 0;

        try {
            EntityData empData = genericEntityService.findById(EMP_TYPE, caller.getUserId(), caller);
            JsonNode p = empData.getPayload();
            ptoUsed     = ptoTotal     - p.path("ptoBalance").asInt(ptoTotal);
            sickUsed    = sickTotal    - p.path("sickBalance").asInt(sickTotal);
            holidayUsed = holidayTotal - p.path("holidayBalance").asInt(holidayTotal);
        } catch (Exception e) {
            log.debug("No Employee record found for {}; returning default balances", caller.getUsername());
        }

        ArrayNode result = objectMapper.createArrayNode();
        result.add(balanceNode("Annual",  ptoUsed,     ptoTotal));
        result.add(balanceNode("Sick",    sickUsed,    sickTotal));
        result.add(balanceNode("Holiday", holidayUsed, holidayTotal));

        return ResponseEntity.ok(result);
    }

    private ObjectNode balanceNode(String type, int used, int total) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("type",  type);
        n.put("used",  used);
        n.put("total", total);
        return n;
    }
}