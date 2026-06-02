package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.coffeeshop.security.Role.HR_MANAGER;
import static com.coffeeshop.security.Role.SHIFT_SUPERVISOR;
import static com.coffeeshop.security.Role.STORE_MANAGER;

// test leave 1: created by barista1, edited, canceled (1-3)
// test leave 2: created by barista1 with 3 requested days --> b6633b14-bb01-49a5-a079-654694c20ba8
// test leave 3: created by barista1 with 13 requested days --> d41270ae-dbf8-4d7b-b238-1fea04f500b5
//               edit with >14 requested sick days   --> rejected - OK
// test leave 4: create with >14 requested sick days --> rejected - OK

// barista1: holidayBalance is 10
// test leave 5: holiday leave for 9 days --> pending
// test leave 6: holiday leave for 3 days --> pending
// hr approves test leave 5 --> approved by system - OK
// hr approves test leave 6 --> rejected by system - OK

// requirements satisfied: leave balance updates for an employee

/**
 * Handles the full Leave Request lifecycle for the HR module.
 * <p>
 * State machine (enforced by ValidationEngineService via Neo4j allowedValues on leaveStatus):
 * <p>
 *   [PENDING] ──────────► [APPROVED]            (HR_MANAGER / STORE_MANAGER)
 *   [PENDING] ──────────► [REJECTED]            (HR_MANAGER / STORE_MANAGER)
 *   [PENDING] ──────────► [CANCELLED]           (employee, own request)
 *   [APPROVED] ─────────► [PENDING_CANCELLATION](employee requests retraction)
 *   [PENDING_CANCELLATION]► [CANCELLED]         (manager confirms retraction — balance restored)
 *   [PENDING_CANCELLATION]► [APPROVED]          (manager denies retraction)
 * <p>
 * leaveStatus allowedValues are defined in Neo4j and validated automatically
 * by ValidationEngineService before any write reaches the database.
 * <p>
 * Balance strategy: Option A — ptoBalance, sickBalance, holidayBalance fields
 * live directly on the Employee EntityData payload and are updated atomically
 * with each APPROVED / CANCELLED transition.
 */
@RestController
@RequestMapping("/api/v1/hr/leave")
@RequiredArgsConstructor
public class LeaveController {

    private static final String LEAVE_TYPE = "LeaveRequest";
    private static final String EMP_TYPE = "Employee";

    /**
     * Transition guard — what statuses are reachable from a given status.
     * The individual STATUS VALUES are now validated by ValidationEngineService
     * (Neo4j allowedValues). This map only guards the directional flow so that
     * e.g. a REJECTED request cannot be moved back to PENDING.
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING", Set.of("APPROVED", "REJECTED", "CANCELLED"),
            "APPROVED", Set.of("PENDING_CANCELLATION"),
            "PENDING_CANCELLATION", Set.of("CANCELLED", "APPROVED"),
            "REJECTED", Set.of(),
            "CANCELLED", Set.of()
    );

    /** Maps leaveType string to the matching balance field name on the Employee payload. */
    private static final Map<String, String> BALANCE_FIELD = Map.of(
            "PTO", "ptoBalance",
            "SICK", "sickBalance",
            "HOLIDAY", "holidayBalance"
    );

    private final GenericEntityService entityService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Submit  →  PENDING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/hr/leave
     * <p>
     * Body: {
     *   "leaveType":  "PTO" | "SICK" | "HOLIDAY",
     *   "startDate":  "2025-07-01",
     *   "endDate":    "2025-07-05",
     *   "notes":      "optional"
     * }
     */
    // OK
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> submitLeaveRequest(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        validateDateRange(body);

        String leaveType = requireText(body, "leaveType").toUpperCase();

        // Peek at current balance before creating — fail fast if insufficient
        EntityData employeeData = entityService.findById(EMP_TYPE, caller.getUserId(), caller);
        JsonNode empPayload = parsePayload(employeeData);
        int daysRequested = countWorkingDays(
                LocalDate.parse(requireText(body, "startDate")),
                LocalDate.parse(requireText(body, "endDate")));
        requireSufficientBalance(empPayload, leaveType, daysRequested);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("employeeId", caller.getUserId().toString());
        payload.put("leaveType", leaveType);
        payload.put("startDate", requireText(body, "startDate"));
        payload.put("endDate", requireText(body, "endDate"));
        payload.put("leaveStatus", "PENDING");
        payload.put("daysRequested", daysRequested);
        if (body.hasNonNull("notes")) payload.put("notes", body.get("notes").asString());

        EntityData created = entityService.create(LEAVE_TYPE, payload, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Edit  →  PENDING only, own request only
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/hr/leave/{id}
     * <p>
     * Partial update — only fields present in the body are changed.
     * Returns 409 if the request is no longer PENDING.
     */
    // OK for pending
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> editLeaveRequest(
            @PathVariable UUID id,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        EntityData existing = entityService.findById(LEAVE_TYPE, id, caller);
        JsonNode current = parsePayload(existing);

        EntityData employeeData = entityService.findById(EMP_TYPE, caller.getUserId(), caller);
        JsonNode empPayload = parsePayload(employeeData);

        String leaveType = requireText(body, "leaveType").toUpperCase();

        requireOwner(current, caller);
        requireStatus(current, "PENDING", "Only PENDING requests can be edited");

        ObjectNode updated = (ObjectNode) current.deepCopy();

        if (body.hasNonNull("leaveType")) updated.put("leaveType", body.get("leaveType").asString().toUpperCase());
        if (body.hasNonNull("startDate")) updated.put("startDate", body.get("startDate").asString());
        if (body.hasNonNull("endDate")) updated.put("endDate", body.get("endDate").asString());
        if (body.hasNonNull("notes")) updated.put("notes", body.get("notes").asString());

        validateDateRange(updated);

        int newDays = countWorkingDays(
                LocalDate.parse(updated.get("startDate").asString()),
                LocalDate.parse(updated.get("endDate").asString()));
        requireSufficientBalance(empPayload, leaveType, newDays);
        updated.put("daysRequested", newDays);

        EntityData result = entityService.update(LEAVE_TYPE, id, updated, caller);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Cancel PENDING  →  CANCELLED  (no balance was deducted yet, no restore needed)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * DELETE /api/v1/hr/leave/{id}
     * <p>
     * Soft-cancel. The record is retained for analytics (UC-AI2).
     * No balance adjustment needed — balance is only touched on APPROVED.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> cancelPendingRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal caller) {

        EntityData existing = entityService.findById(LEAVE_TYPE, id, caller);
        JsonNode current = parsePayload(existing);

        requireOwner(current, caller);
        requireStatus(current, "PENDING",
                "Only PENDING requests can be directly cancelled. " +
                        "For approved requests use POST /api/v1/hr/leave/{id}/retract");

        ObjectNode updated = ((ObjectNode) current).put("leaveStatus", "CANCELLED");
        return ResponseEntity.ok(entityService.update(LEAVE_TYPE, id, updated, caller));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Retract APPROVED  →  PENDING_CANCELLATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/hr/leave/{id}/retract
     * <p>
     * Employee signals they want to cancel an already-approved leave.
     * Balance is NOT restored here — only after a manager confirms via /status.
     * Body: { "reason": "Plans changed" }
     */
    @PostMapping("/{id}/retract")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'STORE_MANAGER', 'SHIFT_SUPERVISOR')")
    public ResponseEntity<EntityData> retractApprovedRequest(
            @PathVariable UUID id,
            @RequestBody(required = false) JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        EntityData existing = entityService.findById(LEAVE_TYPE, id, caller);
        JsonNode current = parsePayload(existing);

        requireOwner(current, caller);
        requireStatus(current, "APPROVED", "Only APPROVED requests can be retracted");

        ObjectNode updated = ((ObjectNode) current).put("leaveStatus", "PENDING_CANCELLATION");
        if (body != null && body.hasNonNull("reason")) {
            updated.put("cancellationReason", body.get("reason").asString());
        }

        return ResponseEntity.ok(entityService.update(LEAVE_TYPE, id, updated, caller));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Manager review  →  drives all manager-side transitions + balance updates
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/hr/leave/{id}/status
     * <p>
     * Valid transitions driven by this endpoint:
     *   PENDING              → APPROVED  (deducts balance from Employee)
     *   PENDING              → REJECTED  (no balance change)
     *   PENDING_CANCELLATION → CANCELLED (restores balance to Employee)
     *   PENDING_CANCELLATION → APPROVED  (manager denies retraction — no balance change)
     * <p>
     * Body: {
     *   "leaveStatus": "APPROVED" | "REJECTED" | "CANCELLED",
     *   "reviewNote":  "optional"
     * }
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'STORE_MANAGER')")
    public ResponseEntity<EntityData> reviewLeaveRequest(
            @PathVariable UUID id,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        String targetStatus = requireText(body, "leaveStatus").toUpperCase();

        EntityData leaveData = entityService.findById(LEAVE_TYPE, id, caller);
        JsonNode current = parsePayload(leaveData);
        String currentStatus = requireText(current, "leaveStatus").toUpperCase();

        // Guard the directional flow (allowedValues in Neo4j guards individual value validity)
        validateTransition(currentStatus, targetStatus);

        // ── Option A: balance deduction / restoration ─────────────────────
        String leaveType = requireText(current, "leaveType").toUpperCase();
        int daysRequested = current.path("daysRequested").asInt(0);
        UUID employeeId = UUID.fromString(requireText(current, "employeeId"));

        if ("APPROVED".equals(targetStatus) && "PENDING".equals(currentStatus)) {
            // Deduct balance — re-validate in case balance changed since submission
            EntityData empData = entityService.findById(EMP_TYPE, employeeId, caller);
            JsonNode empPayload = parsePayload(empData);
            requireSufficientBalance(empPayload, leaveType, daysRequested);
            adjustBalance(employeeId, leaveType, -daysRequested, caller);
        }

        if ("CANCELLED".equals(targetStatus) && "PENDING_CANCELLATION".equals(currentStatus)) {
            // Restore balance — leave was previously approved so balance was already deducted
            adjustBalance(employeeId, leaveType, +daysRequested, caller);
        }
        // ── End balance logic ─────────────────────────────────────────────

        ObjectNode updated = ((ObjectNode) current).put("leaveStatus", targetStatus);
        updated.put("reviewedBy", caller.getUserId().toString());
        if (body.hasNonNull("reviewNote")) {
            updated.put("reviewNote", body.get("reviewNote").asString());
        }

        return ResponseEntity.ok(entityService.update(LEAVE_TYPE, id, updated, caller));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Read endpoints
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'STORE_MANAGER', 'SHIFT_SUPERVISOR')")
    public ResponseEntity<Page<EntityData>> getAllLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal caller) {

        Page<EntityData> result = entityService.findByTwoPayloadFields(
                LEAVE_TYPE,
                "leaveStatus", status,        // null → condition skipped
                "leaveType", leaveType,                 // null → condition skipped
                pageable, caller);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/hr/leave/{id}
     * Employees can only view their own. Managers can view any.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> getLeaveRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal caller) {

        EntityData entity = entityService.findById(LEAVE_TYPE, id, caller);
        JsonNode payload = entity.getPayload();

        boolean isOwner = caller.getUserId().toString()
                .equals(payload.path("employeeId").asString());
        boolean isManager = caller.hasAnyRole(HR_MANAGER, STORE_MANAGER, SHIFT_SUPERVISOR);

        if (!isOwner && !isManager) {
            throw new AccessDeniedException("You can only view your own leave requests");
        }
        return ResponseEntity.ok(entity);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Atomically adjusts a balance field on the Employee payload.
     * delta is negative to deduct, positive to restore.
     */
    private void adjustBalance(UUID employeeId, String leaveType, int delta,
                               UserPrincipal caller) {
        String balanceField = BALANCE_FIELD.get(leaveType);
        if (balanceField == null) return; // non-balance leave types (e.g. unpaid) — no-op

        EntityData empData = entityService.findById(EMP_TYPE, employeeId, caller);
        ObjectNode empPayload = (ObjectNode) parsePayload(empData);

        int current = empPayload.path(balanceField).asInt(0);
        int updated = current + delta;

        if (updated < 0) {
            // Shouldn't happen if requireSufficientBalance was called first, but guard anyway
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Balance adjustment would result in negative " + balanceField);
        }

        empPayload.put(balanceField, updated);
        entityService.update(EMP_TYPE, employeeId, empPayload, caller);
    }

    /**
     * Throws 422 if the employee does not have enough balance for the requested leave type.
     * Only checked for leave types that map to a balance field (PTO, SICK, HOLIDAY).
     */
    private void requireSufficientBalance(JsonNode empPayload, String leaveType, int daysNeeded) {
        String balanceField = BALANCE_FIELD.get(leaveType);
        if (balanceField == null) return; // unpaid or untracked leave type — skip

        int available = empPayload.path(balanceField).asInt(0);
        if (available < daysNeeded) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Insufficient " + balanceField + ": " + available +
                            " days available, " + daysNeeded + " requested");
        }
    }

    /**
     * Counts weekdays (Mon–Fri) between startDate inclusive and endDate inclusive.
     * Public holidays are NOT currently excluded — add a Holiday calendar entity
     * to Neo4j if that level of accuracy is needed.
     */
    private int countWorkingDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            int dow = cursor.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
            if (dow <= 5) count++;
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private JsonNode parsePayload(EntityData entity) {
        try {
            return objectMapper.readTree(String.valueOf(entity.getPayload()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Corrupt payload for entity " + entity.getId());
        }
    }

    private void requireOwner(JsonNode payload, UserPrincipal caller) {
        if (!caller.getUserId().toString().equals(payload.path("employeeId").asString())) {
            throw new AccessDeniedException("You can only modify your own leave requests");
        }
    }

    private void requireStatus(JsonNode payload, String expected, String message) {
        String actual = payload.path("leaveStatus").asString();
        if (!expected.equalsIgnoreCase(actual)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    message + " (current status: " + actual + ")");
        }
    }

    private void validateTransition(String from, String to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transition from " + from + " to " + to + " is not permitted");
        }
    }

    private void validateDateRange(JsonNode body) {
        String startStr = body.path("startDate").asString(null);
        String endStr = body.path("endDate").asString(null);
        if (startStr == null || endStr == null) return;

        LocalDate start = LocalDate.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate end = LocalDate.parse(endStr, DateTimeFormatter.ISO_LOCAL_DATE);

        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "endDate cannot be before startDate");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "startDate cannot be in the past");
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required field: " + field);
        }
        return value.asString();
    }
}