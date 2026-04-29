package com.coffeeshop.web;

import com.coffeeshop.exception.EntityNotFoundException;
import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.Role;
import com.coffeeshop.security.UserPrincipal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    private final ObjectMapper objectMapper;

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

        String employeeId = requireText(body, "employeeId");
        String storeLocationId = requireText(body, "storeLocationId");

        // UC-HR2: STORE_MANAGER can only schedule shifts at their own location
        enforceLocationScope(caller, storeLocationId);

        // UC-HR2: Verify no overlapping SCHEDULED or ACTIVE shift exists for this employee
        validateNoShiftConflict(employeeId, requireText(body, "shiftDate"), caller);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("employeeId", employeeId);
        payload.put("storeLocationId", storeLocationId);
        payload.put("shiftDate", requireText(body, "shiftDate"));
        payload.put("startTime", requireText(body, "startTime"));
        payload.put("endTime", requireText(body, "endTime"));
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

        String employeeId = requireText(body, "employeeId");
        String storeLocationId = requireText(body, "storeLocationId");
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
            JsonNode activePayload = parsePayload(activeShift);
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
                    JsonNode p = parsePayload(s);
                    return today.equals(p.path("shiftDate").asString())
                            && storeLocationId.equals(p.path("storeLocationId").asString());
                })
                .findFirst()
                .orElse(null);

        EntityData result;

        if (shiftToActivate != null) {
            // Activate the pre-scheduled shift
            ObjectNode updated = (ObjectNode) parsePayload(shiftToActivate);
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

        UUID shiftId = UUID.fromString(requireText(body, "shiftId"));

        log.info("Clock-out request: shiftId={}, requester={}", shiftId, caller.getUsername());

        EntityData shiftData = entityService.findById(SHIFT_TYPE, shiftId, caller);
        JsonNode current = parsePayload(shiftData);

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
        JsonNode current = parsePayload(shiftData);

        String status = current.path("shiftStatus").asString();
        if (!"SCHEDULED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only SCHEDULED shifts can be cancelled (current status: " + status + ")");
        }

        enforceLocationScope(caller, current.path("storeLocationId").asString());

        ObjectNode updated = ((ObjectNode) current).put("shiftStatus", "CANCELLED");
        return ResponseEntity.ok(entityService.update(SHIFT_TYPE, id, updated, caller));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule view  →  planned + active + completed shifts in a date range
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/hr/schedule/{storeLocationId}?startDate=&endDate=&page=0&size=100
     * <p>
     * Returns all Shift records for a location in the date range, enriched with
     * the employee's fullName and position from their Employee EntityData.
     * <p>
     * Each entry in the response carries a derived "shiftMode" field:
     *   "PLANNED"   — shiftStatus=SCHEDULED, no clockInTime yet
     *   "ACTIVE"    — clockedIn but not yet clocked out
     *   "COMPLETED" — fully clocked in and out
     *   "CANCELLED" — shift was canceled before it started
     * <p>
     * Access: SHIFT_SUPERVISOR and HR_MANAGER see any location.
     *         STORE_MANAGER is restricted to their own storeLocationId from the JWT.
     */
    @GetMapping("/schedule/{storeLocationId}")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER', 'HR_MANAGER')")
    public ResponseEntity<Page<ScheduleEntryResponse>> getSchedule(
            @PathVariable UUID storeLocationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal caller) {

        log.info("Schedule query: location={}, {}->{}, requester={}",
                storeLocationId, startDate, endDate, caller.getUsername());

        enforceLocationScope(caller, storeLocationId.toString());

        Page<EntityData> shifts = entityService.findByPayloadFieldInDateRange(
                SHIFT_TYPE,
                "storeLocationId", storeLocationId.toString(),
                "shiftDate", startDate, endDate,
                pageable, caller);

        Page<ScheduleEntryResponse> enriched = shifts
                .map(shift -> enrichShiftWithEmployee(shift, caller));

        return ResponseEntity.ok(enriched);
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

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Derives shiftMode from the shift payload fields:
     *   clockInTime == null  AND shiftStatus == SCHEDULED  → PLANNED
     *   clockInTime != null  AND clockOutTime == null      → ACTIVE
     *   clockOutTime != null                               → COMPLETED
     *   shiftStatus == CANCELLED                           → CANCELLED
     */
    private String deriveShiftMode(JsonNode payload) {
        String status = payload.path("shiftStatus").asString("");
        String clockInTime = payload.path("clockInTime").asString(null);
        String clockOut = payload.path("clockOutTime").asString(null);

        if ("CANCELLED".equals(status)) return "CANCELLED";
        if (clockInTime == null || clockInTime.isBlank()) return "PLANNED";
        if (clockOut == null || clockOut.isBlank()) return "ACTIVE";
        return "COMPLETED";
    }

    /**
     * Looks up the Employee EntityData for the shift and builds a ScheduleEntryResponse.
     * If the employee record is missing (deleted/corrupted), employee fields are populated
     * with fallback values rather than failing the entire schedule query.
     */
    private ScheduleEntryResponse enrichShiftWithEmployee(EntityData shift, UserPrincipal caller) {
        JsonNode sp = parsePayload(shift);
        String employeeId = sp.path("employeeId").asString(null);

        String employeeFullName = "Unknown";
        String employeePosition = "Unknown";

        if (employeeId != null) {
            try {
                EntityData emp = entityService.findById(
                        EMPLOYEE_TYPE, UUID.fromString(employeeId), caller);
                JsonNode ep = parsePayload(emp);
                employeeFullName = ep.path("fullName").asString("Unknown");
                employeePosition = ep.path("position").asString("Unknown");
            } catch (EntityNotFoundException e) {
                log.warn("Employee not found for shift {}: employeeId={}", shift.getId(), employeeId);
            }
        }

        return new ScheduleEntryResponse(
                shift.getId(),
                sp.path("shiftDate").asString(null),
                sp.path("startTime").asString(null),
                sp.path("endTime").asString(null),
                sp.path("shiftStatus").asString(null),
                deriveShiftMode(sp),
                nullIfBlank(sp.path("clockInTime").asString(null)),
                nullIfBlank(sp.path("clockOutTime").asString(null)),
                sp.path("mealBreakMinutes").asInt(0),
                sp.path("restBreakMinutes").asInt(0),
                sp.path("paidMinutes").isNull() || sp.path("paidMinutes").isMissingNode()
                        ? null : sp.path("paidMinutes").asInt(),
                employeeId,
                employeeFullName,
                employeePosition
        );
    }

    /**
     * UC-HR2: Checks whether the employee already has a SCHEDULED or ACTIVE shift
     * on the given date, to prevent double-booking via the schedule endpoint.
     * Clock-in has its own real-time ACTIVE check — this guard is for pre-scheduling.
     */
    private void validateNoShiftConflict(String employeeId, String shiftDate,
                                         UserPrincipal caller) {
        Page<EntityData> existing = entityService.findByPayloadField(
                SHIFT_TYPE, "employeeId", employeeId, Pageable.unpaged(), caller);

        boolean conflict = existing.getContent().stream().anyMatch(s -> {
            JsonNode p = parsePayload(s);
            String date = p.path("shiftDate").asString("");
            String status = p.path("shiftStatus").asString("");
            return shiftDate.equals(date)
                    && (status.equals("SCHEDULED") || status.equals("ACTIVE"));
        });

        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Employee already has a SCHEDULED or ACTIVE shift on " + shiftDate +
                            ". Cancel the existing shift first.");
        }
    }

    /**
     * STORE_MANAGER is location-scoped to their JWT storeLocationId.
     * HR_MANAGER and SHIFT_SUPERVISOR can access any location.
     */
    private void enforceLocationScope(UserPrincipal caller, String targetLocationId) {
        if (caller.hasRole(Role.STORE_MANAGER)
                && caller.getStoreLocationId() != null
                && !caller.getStoreLocationId().toString().equals(targetLocationId)) {
            throw new AccessDeniedException(
                    "STORE_MANAGER is restricted to their assigned location");
        }
    }

    private JsonNode parsePayload(EntityData entity) {
        try {
            return objectMapper.readTree(String.valueOf(entity.getPayload()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Corrupt payload for entity " + entity.getId());
        }
    }

    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
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