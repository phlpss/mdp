package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.security.UserPrincipal;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Human Resources module controller for employee lifecycle management.
 * <p>
 * Implements business rules from:
 * - UC-HR1: Employee onboarding
 * - UC-HR2: Shift management with dual-location conflict detection
 * - UC-HR3: Leave request approval
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr")
public class HrController {

    /**
     * Approve or reject a leave request.
     * <p>
     * Business Rule (UC-HR3):
     * When a leave request is APPROVED:
     * 1. Update the LeaveRequest EntityData status to APPROVED
     * 2. Deduct approved leave days from the employee's leave balance field
     * 3. Publish approval event to Pub/Sub for notification consumers
     * 4. If REJECTED, simply update status and publish rejection event
     * <p>
     * Access: SHIFT_SUPERVISOR or STORE_MANAGER
     *
     * @param leaveRequestId UUID of the leave request entity
     * @param body Request body: { "status": "APPROVED"|"REJECTED", "reviewNote": "..." }
     * @param caller Authenticated user
     * @return Updated LeaveRequest entity with 200 status
     */
    @PatchMapping("/leave/{leaveRequestId}/status")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER')")
    public ResponseEntity<EntityData> updateLeaveStatus(@PathVariable UUID leaveRequestId, @RequestBody JsonNode body, @AuthenticationPrincipal UserPrincipal caller) {

        String status = body.get("status").asText();
        String reviewNote = body.has("reviewNote") ? body.get("reviewNote").asText() : "";

        log.info("Leave request review: id={}, status={}, reviewer={}", leaveRequestId, status, caller.getUsername());

        // TODO UC-HR3: Leave request approval workflow
        // 1. Fetch LeaveRequest EntityData by ID
        // 2. Validate current status is PENDING
        // 3. If status == APPROVED:
        //    a. Query employee's leave balance from EntityData payload
        //    b. Deduct approved days count
        //    c. Update and persist
        //    d. Publish LEAVE_APPROVED event to Pub/Sub
        // 4. If status == REJECTED:
        //    a. Update status to REJECTED
        //    b. Publish LEAVE_REJECTED event to Pub/Sub
        // 5. Log audit trail with reviewer ID and note
        // 6. Return updated LeaveRequest

        return ResponseEntity.ok().build();
    }

    /**
     * Record clock-in for an employee at a store location.
     * <p>
     * Business Rule (UC-HR2 - Dual-Location Conflict):
     * Before allowing clock-in, verify the employee is NOT already clocked in at
     * a different location. If a conflict is detected, reject with 409 Conflict status.
     * <p>
     * Access: Any authenticated user (typically BARISTA, WAITER, CASHIER)
     *
     * @param body Request body: { "employeeId": "...", "storeLocationId": "..." }
     * @param caller Authenticated user
     * @return Created ShiftRecord entity with 201 status
     */
    @PostMapping("/clock-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> clockIn(@RequestBody JsonNode body, @AuthenticationPrincipal UserPrincipal caller) {

        String employeeId = body.get("employeeId").asText();
        String storeLocationId = body.get("storeLocationId").asText();

        log.info("Clock-in request: employeeId={}, storeLocationId={}, requester={}", employeeId, storeLocationId, caller.getUsername());

        // TODO UC-HR2: Dual-location conflict detection
        // 1. Query for active ShiftRecord entities with:
        //    - employeeId matching the request
        //    - clockOutTime is NULL (not yet clocked out)
        // 2. If any active shift exists:
        //    a. Check if storeLocationId differs from the active shift
        //    b. If different, return 409 Conflict with error details
        //    c. If same location, return 400 Bad Request (already clocked in)
        // 3. If no active shifts found:
        //    a. Create new ShiftRecord EntityData with:
        //       - employeeId
        //       - storeLocationId
        //       - clockInTime = now()
        //       - clockOutTime = NULL
        //       - breakMinutes = 0
        //       - paidTime = NULL (calculated on clock-out)
        //    b. Persist and publish CLOCK_IN event to Pub/Sub
        //    c. Return created ShiftRecord with 201 status

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * Record clock-out for an employee's current shift.
     * <p>
     * Business Rule (UC-HR2):
     * Upon clock-out, calculate paid work time:
     * - paidTime = (clockOutTime - clockInTime) - mealBreakMinutes - restBreakMinutes
     * <p>
     * Access: Any authenticated user
     *
     * @param body Request body: { "shiftRecordId": "...", "breakMinutes": ... }
     * @param caller Authenticated user
     * @return Updated ShiftRecord entity with 200 status
     */
    @PostMapping("/clock-out")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EntityData> clockOut(@RequestBody JsonNode body, @AuthenticationPrincipal UserPrincipal caller) {

        String shiftRecordId = body.get("shiftRecordId").asText();
        int breakMinutes = body.has("breakMinutes") ? body.get("breakMinutes").asInt() : 0;

        log.info("Clock-out request: shiftRecordId={}, breakMinutes={}, requester={}", shiftRecordId, breakMinutes, caller.getUsername());

        // TODO UC-HR2: Calculate and persist shift duration
        // 1. Fetch ShiftRecord EntityData by ID
        // 2. Validate clockOutTime is still NULL (not already clocked out)
        // 3. Calculate:
        //    - totalMinutes = (now - clockInTime) / 60
        //    - paidTime = totalMinutes - breakMinutes
        // 4. Update ShiftRecord with:
        //    - clockOutTime = now()
        //    - breakMinutes (from request)
        //    - paidTime (calculated)
        // 5. Persist and publish CLOCK_OUT event to Pub/Sub
        // 6. Return updated ShiftRecord

        return ResponseEntity.ok().build();
    }

    /**
     * Retrieve the work schedule for a store location and date range.
     * <p>
     * Returns all Shift EntityData for the given store with employee details
     * joined from their Employee EntityData records. Respects location-scoped authorization.
     * <p>
     * Access: SHIFT_SUPERVISOR, STORE_MANAGER, or HR_MANAGER
     *
     * @param storeLocationId UUID of the store
     * @param startDate ISO-8601 date (e.g., 2026-04-10)
     * @param endDate ISO-8601 date
     * @param caller Authenticated user
     * @return List of scheduled shifts with employee details
     */
    @GetMapping("/schedule/{storeLocationId}")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER', 'HR_MANAGER')")
    public ResponseEntity<Object> getSchedule(@PathVariable UUID storeLocationId, @RequestParam String startDate, @RequestParam String endDate, @AuthenticationPrincipal UserPrincipal caller) {

        log.info("Schedule query: storeLocationId={}, startDate={}, endDate={}, requester={}", storeLocationId, startDate, endDate, caller.getUsername());

        // TODO UC-HR1: Retrieve scheduled shifts with employee details
        // 1. Validate caller has permission to view location (location scoping)
        // 2. Query Shift EntityData with filters:
        //    - storeLocationId matches
        //    - shiftDate between startDate and endDate
        //    - Paginate by 100 shifts per page (support ?page=0&size=100)
        // 3. For each shift, look up Employee EntityData by employeeId
        // 4. Join and aggregate into ScheduleResponse DTO with employee names, roles
        // 5. Publish metrics event to Pub/Sub for analytics (shift coverage analysis)
        // 6. Return list of shifts with embedded employee details

        return ResponseEntity.ok().build();
    }
}

