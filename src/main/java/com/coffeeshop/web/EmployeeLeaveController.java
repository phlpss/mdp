package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.Role;
import com.coffeeshop.security.UserPrincipal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin employee-facing leave endpoints at /api/v1/leaves.
 * <p>
 * The main leave lifecycle lives in LeaveController (/api/v1/hr/leave).
 * This controller exposes the employee self-service paths the Angular
 * employee-dashboard calls directly.
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
     * GET /api/v1/leaves/balance?employeeId={uuid}
     * Returns the leave balance summary for the given employee.
     * If employeeId is omitted, defaults to the caller's own balance.
     * Managers (HR_MANAGER, STORE_MANAGER, BUSINESS_OWNER) may query any employee.
     * Regular employees may only query themselves.
     */
    @GetMapping("/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ArrayNode> getLeaveBalance(
            @RequestParam(required = false) UUID employeeId,
            @AuthenticationPrincipal UserPrincipal caller) {

        UUID targetId = (employeeId != null) ? employeeId : caller.getUserId();

        boolean isSelf    = targetId.equals(caller.getUserId());
        boolean isManager = caller.hasAnyRole(Role.HR_MANAGER, Role.STORE_MANAGER, Role.BUSINESS_OWNER);

        if (!isSelf && !isManager) {
            throw new AccessDeniedException("You can only view your own leave balance");
        }

        log.debug("GET /api/v1/leaves/balance - caller={}, targetId={}", caller.getUsername(), targetId);

        int ptoBalance     = 0;
        int sickBalance    = 0;
        int holidayBalance = 0;
        int ptoTotal       = 0;
        int sickTotal      = 0;
        int holidayTotal   = 0;

        try {
            EntityData empData = entityService.findById(EMP_TYPE, targetId, caller);
            JsonNode p = empData.getPayload();
            ptoBalance     = p.path("ptoBalance").asInt(0);
            sickBalance    = p.path("sickBalance").asInt(0);
            holidayBalance = p.path("holidayBalance").asInt(0);
            ptoTotal       = p.path("ptoTotal").asInt(ptoBalance);
            sickTotal      = p.path("sickTotal").asInt(sickBalance);
            holidayTotal   = p.path("holidayTotal").asInt(holidayBalance);
        } catch (Exception e) {
            log.debug("No Employee record found for id={}; returning zero balances", targetId);
        }

        ArrayNode result = objectMapper.createArrayNode();
        result.add(balanceNode("Annual",  ptoTotal     - ptoBalance,     ptoTotal));
        result.add(balanceNode("Sick",    sickTotal    - sickBalance,    sickTotal));
        result.add(balanceNode("Holiday", holidayTotal - holidayBalance, holidayTotal));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<EntityData>> getMyLeaveRequests(
            Pageable pageable, @AuthenticationPrincipal UserPrincipal caller) {

        Page<EntityData> mine = entityService.findByPayloadField(
                LEAVE_TYPE, "employeeId", caller.getUserId().toString(), pageable, caller);
        return ResponseEntity.ok(mine);
    }

    private ObjectNode balanceNode(String type, int used, int total) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("type",  type);
        n.put("used",  used);
        n.put("total", total);
        return n;
    }
}