package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.EntityDataRepository;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import com.coffeeshop.service.GeneralService;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Human Resources module — shift lifecycle and schedule management.
 * <p>
 * Implements:
 *   UC-HR2: Shift management with dual-location conflict detection,
 *           paid time calculation on clock-out.
 * <p>
 * A single Shift EntityData record covers both the planned schedule entry
 * and the live clock-in/out record. The shiftStatus field drives the lifecycle:
 * <p>
 *   SCHEDULED  → clock-in  → ACTIVE  → clock-out → COMPLETED
 *   SCHEDULED  → cancel            → CANCELLED
 * <p>
 * clockInTime / clockOutTime being null signals a purely planned shift
 * so the schedule endpoint can mark it accordingly.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrController {

    private static final String SHIFT_TYPE = "Shift";
    private static final String EMPLOYEE_TYPE = "Employee";

    private final GenericEntityService entityService;
    private final EntityDataRepository entityDataRepository;
    private final ObjectMapper objectMapper;
    private final GeneralService generalService;
    private final PasswordEncoder passwordEncoder;
    /**
     +     * POST /api/v1/hr/employees
     +     * Creates an Employee entity and a linked User account atomically.
     +     * Request body: employee payload + "password" field (plain text, hashed here).
     +     */
    @PostMapping("/employees")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'IT_SPECIALIST')")
    public ResponseEntity<EntityData> createEmployee(@RequestBody JsonNode body, @AuthenticationPrincipal UserPrincipal caller) {

        String password = body.has("password") ? body.get("password").asString() : null;
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Strip password from employee payload before storing
        ObjectNode employeePayload = (ObjectNode) body.deepCopy();
        employeePayload.remove("password");
        EntityData employee = entityService.create("Employee", employeePayload, caller);

        // Derive username from email or fallback to employeeId
        String username = employeePayload.path("email").asString(employee.getId().toString());
        String role = employeePayload.path("role").asString("BARISTA");
        String fullName = employeePayload.path("fullName").asString(null);
        String email = employeePayload.path("email").asString(null);

        ObjectNode userPayload = objectMapper.createObjectNode();
        userPayload.put("fullName", fullName);
        userPayload.put("email", email);
        userPayload.put("username", username);
        userPayload.put("passwordHash", passwordEncoder.encode(password));
        userPayload.put("isActive", true);
        userPayload.put("employeeId", employee.getId().toString());
        userPayload.putArray("roles").add(role.toUpperCase());
        String locId = employeePayload.path("storeLocationId").asString(null);
        if (locId != null) userPayload.put("storeLocationId", locId);

        entityDataRepository.save(EntityData.builder().type("User").payload(userPayload).build());
        log.info("User created: employeeId={}, username={}", employee.getId(), username);

        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule a shift (manager pre-assigns)  →  shiftStatus: SCHEDULED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/hr/shifts
     * <p>
     * Manager pre-creates a planned shift for an employee at a location.
     * The employee can then clock in against this shift, or a new shift
     * is created on-the-fly at clock-in time if no planned shift exists.
     * <p>
     * Body: {
     *   "employeeId":      "uuid",
     *   "storeLocationId": "uuid",
     *   "shiftDate":       "2026-05-01",
     *   "startTime":       "2026-05-01T08:00:00",
     *   "endTime":         "2026-05-01T16:00:00",
     *   "mealBreakMinutes": 30,
     *   "restBreakMinutes": 15
     * }
     * <p>
     * Access: SHIFT_SUPERVISOR, STORE_MANAGER, HR_MANAGER
     */
    @PostMapping("/shifts")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER', 'HR_MANAGER')")
    public ResponseEntity<EntityData> scheduleShift(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        String employeeId = generalService.requireText(body, "employeeId");
        String storeLocationId = generalService.requireText(body, "storeLocationId");

        // UC-HR2: STORE_MANAGER can only schedule shifts at their own location
        generalService.enforceLocationScope(caller, storeLocationId);

        // UC-HR2: Verify no overlapping SCHEDULED or ACTIVE shift exists for this employee
        generalService.validateNoShiftConflict(employeeId, generalService.requireText(body, "shiftDate"), caller);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("employeeId", employeeId);
        payload.put("storeLocationId", storeLocationId);
        payload.put("shiftDate", generalService.requireText(body, "shiftDate"));
        payload.put("startTime", generalService.requireText(body, "startTime"));
        payload.put("endTime", generalService.requireText(body, "endTime"));
        payload.put("mealBreakMinutes", body.path("mealBreakMinutes").asInt(0));
        payload.put("restBreakMinutes", body.path("restBreakMinutes").asInt(0));
        payload.put("shiftStatus", "SCHEDULED");
        // clockInTime, clockOutTime, breakMinutes, paidMinutes intentionally absent
        // Their absence signals this is a purely planned shift

        EntityData created = entityService.create(SHIFT_TYPE, payload, caller);
        log.info("Shift scheduled: employeeId={}, date={}, by={}",
                employeeId, body.path("shiftDate").asString(), caller.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clock-in  →  shiftStatus: ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/hr/clock-in
     * <p>
     * UC-HR2 dual-location conflict: if the employee has an ACTIVE shift at a
     * different location, the request is rejected with 409.
     * <p>
     * If a SCHEDULED shift exists for today at this location, it is activated
     * (clockInTime stamped, status → ACTIVE). Otherwise, a new ad-hoc shift
     * record is created — covering walk-in employees with no pre-assigned shift.
     * <p>
     * Body: {
     *   "employeeId":      "uuid",
     *   "storeLocationId": "uuid"
     * }
     * <p>
     * Access: Any authenticated user (employee clocking themselves in)
     */
    @PostMapping("/clock-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> clockIn(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        String employeeId = generalService.requireText(body, "employeeId");
        String storeLocationId = generalService.requireText(body, "storeLocationId");
        String today = LocalDate.now().toString();

        log.info("Clock-in request: employeeId={}, storeLocationId={}, requester={}",
                employeeId, storeLocationId, caller.getUsername());

        // UC-HR2: Dual-location conflict detection
        // Check for any shift with shiftStatus=ACTIVE for this employee
        Page<EntityData> activeShifts = entityService.findByTwoPayloadFields(
                SHIFT_TYPE,
                "employeeId", employeeId,
                "shiftStatus", "ACTIVE",
                Pageable.unpaged(), caller);

        if (!activeShifts.isEmpty()) {
            EntityData activeShift = activeShifts.getContent().getFirst();
            JsonNode activePayload = generalService.parsePayload(activeShift);
            String activeLocation = activePayload.path("storeLocationId").asString();

            if (activeLocation.equals(storeLocationId)) {
                // Same location — already clocked in here
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Employee is already clocked in at this location " +
                                "(shiftId=" + activeShift.getId() + ")");
            } else {
                // Different location — UC-HR2 conflict
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Employee is already clocked in at a different location " +
                                "(storeLocationId=" + activeLocation + ", shiftId=" + activeShift.getId() + "). " +
                                "Clock out there first.");
            }
        }

        // Look for a pre-scheduled shift for today at this location
        Page<EntityData> scheduledToday = entityService.findByTwoPayloadFields(
                SHIFT_TYPE,
                "employeeId", employeeId,
                "shiftStatus", "SCHEDULED",
                Pageable.unpaged(), caller);

        EntityData shiftToActivate = scheduledToday.getContent().stream()
                .filter(s -> {
                    JsonNode p = generalService.parsePayload(s);
                    return today.equals(p.path("shiftDate").asString())
                            && storeLocationId.equals(p.path("storeLocationId").asString());
                })
                .findFirst()
                .orElse(null);

        EntityData result;

        if (shiftToActivate != null) {
            // Activate the pre-scheduled shift
            ObjectNode updated = (ObjectNode) generalService.parsePayload(shiftToActivate);
            updated.put("clockInTime", LocalDateTime.now().toString());
            updated.put("shiftStatus", "ACTIVE");
            result = entityService.update(SHIFT_TYPE, shiftToActivate.getId(), updated, caller);
            log.info("Activated scheduled shift: shiftId={}, employeeId={}", shiftToActivate.getId(), employeeId);
        } else {
            // No pre-scheduled shift — create an ad-hoc one
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("employeeId", employeeId);
            payload.put("storeLocationId", storeLocationId);
            payload.put("shiftDate", today);
            payload.put("startTime", LocalDateTime.now().toString());
            payload.put("endTime", (String) null);   // unknown until clock-out
            payload.put("clockInTime", LocalDateTime.now().toString());
            payload.put("shiftStatus", "ACTIVE");
            payload.put("mealBreakMinutes", 0);
            payload.put("restBreakMinutes", 0);
            result = entityService.create(SHIFT_TYPE, payload, caller);
            log.info("Created ad-hoc shift on clock-in: employeeId={}, location={}", employeeId, storeLocationId);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clock-out  →  shiftStatus: COMPLETED, paidMinutes calculated
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/hr/clock-out
     * <p>
     * UC-HR2 paid time rule:
     *   paidMinutes = (clockOutTime − clockInTime) − mealBreakMinutes − restBreakMinutes
     * <p>
     * breakMinutes in the request body overrides whatever was on the planned shift
     * (actual break taken may differ from the scheduled break allocation).
     * <p>
     * Body: {
     *   "shiftId":          "uuid",
     *   "mealBreakMinutes": 30,   (optional, defaults to value on shift record)
     *   "restBreakMinutes": 15    (optional, defaults to value on shift record)
     * }
     * <p>
     * Access: Any authenticated user
     */
    @PostMapping("/clock-out")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> clockOut(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        UUID shiftId = UUID.fromString(generalService.requireText(body, "shiftId"));

        log.info("Clock-out request: shiftId={}, requester={}", shiftId, caller.getUsername());

        EntityData shiftData = entityService.findById(SHIFT_TYPE, shiftId, caller);
        JsonNode current = generalService.parsePayload(shiftData);

        // Guard: must be ACTIVE
        String status = current.path("shiftStatus").asString();
        if (!"ACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot clock out: shift is not ACTIVE (current status: " + status + ")");
        }

        // Guard: must have a clockInTime
        String clockInStr = current.path("clockInTime").asString(null);
        if (clockInStr == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Shift " + shiftId + " is ACTIVE but has no clockInTime — data inconsistency");
        }

        LocalDateTime clockIn = LocalDateTime.parse(clockInStr);
        LocalDateTime clockOut = LocalDateTime.now();

        // UC-HR2: paidMinutes = totalMinutes − mealBreakMinutes − restBreakMinutes
        long totalMinutes = ChronoUnit.MINUTES.between(clockIn, clockOut);

        int mealBreak = body.hasNonNull("mealBreakMinutes")
                ? body.get("mealBreakMinutes").asInt()
                : current.path("mealBreakMinutes").asInt(0);
        int restBreak = body.hasNonNull("restBreakMinutes")
                ? body.get("restBreakMinutes").asInt()
                : current.path("restBreakMinutes").asInt(0);

        int totalBreak = mealBreak + restBreak;
        int paidMinutes = (int) Math.max(0, totalMinutes - totalBreak);

        ObjectNode updated = (ObjectNode) current.deepCopy();
        updated.put("clockOutTime", clockOut.toString());
        updated.put("mealBreakMinutes", mealBreak);
        updated.put("restBreakMinutes", restBreak);
        updated.put("paidMinutes", paidMinutes);
        updated.put("shiftStatus", "COMPLETED");
        // Stamp endTime if it was an ad-hoc shift (endTime was null at clock-in)
        if (updated.path("endTime").isNull() || updated.path("endTime").isMissingNode()) {
            updated.put("endTime", clockOut.toString());
        }

        EntityData result = entityService.update(SHIFT_TYPE, shiftId, updated, caller);
        log.info("Clock-out complete: shiftId={}, paidMinutes={}, employee={}",
                shiftId, paidMinutes, current.path("employeeId").asString());

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel a scheduled shift
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/hr/shifts/{id}/cancel
     * <p>
     * Only SCHEDULED shifts can be canceled. An ACTIVE shift must be clocked out first.
     * STORE_MANAGER can only cancel shifts at their own location.
     * <p>
     * Access: SHIFT_SUPERVISOR, STORE_MANAGER, HR_MANAGER
     */
    @PatchMapping("/shifts/{id}/cancel")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER', 'HR_MANAGER')")
    public ResponseEntity<EntityData> cancelShift(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal caller) {

        EntityData shiftData = entityService.findById(SHIFT_TYPE, id, caller);
        JsonNode current = generalService.parsePayload(shiftData);

        String status = current.path("shiftStatus").asString();
        if (!"SCHEDULED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only SCHEDULED shifts can be cancelled (current status: " + status + ")");
        }

        generalService.enforceLocationScope(caller, current.path("storeLocationId").asString());

        ObjectNode updated = ((ObjectNode) current).put("shiftStatus", "CANCELLED");
        return ResponseEntity.ok(entityService.update(SHIFT_TYPE, id, updated, caller));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response DTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Read-only projection returned by getSchedule().
     * Combines Shift fields with a subset of Employee fields.
     * "shiftMode" is derived — not stored in Neo4j or PostgreSQL.
     */
    public record ScheduleEntryResponse(
            UUID shiftId,
            String shiftDate,
            String startTime,
            String endTime,
            String shiftStatus,
            String shiftMode,          // PLANNED | ACTIVE | COMPLETED | CANCELLED
            String clockInTime,        // null if not yet clocked in
            String clockOutTime,       // null if not yet clocked out
            Integer mealBreakMinutes,
            Integer restBreakMinutes,
            Integer paidMinutes,        // null until COMPLETED
            // Employee summary
            String employeeId,
            String employeeFullName,   // from Employee payload
            String employeePosition    // from Employee payload
    ) {
    }
}