package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import com.coffeeshop.service.GeneralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Thin employee-facing shift endpoints at /api/v1/shifts.
 * <p>
 * The main shift management lives in HrController (/api/v1/hr/shifts).
 * This controller exposes the employee self-service paths that the Angular
 * employee-dashboard calls directly.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class EmployeeShiftController {

    private static final String SHIFT_TYPE = "Shift";

    private final GenericEntityService genericEntityService;
    private final GeneralService generalService;
    private final GenericEntityService entityService;

    /**
     * GET /api/v1/shifts/my/upcoming
     * Returns upcoming SCHEDULED shifts for the currently authenticated employee.
     */
    @GetMapping("/my/upcoming")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EntityData>> getMyUpcomingShifts(
            @AuthenticationPrincipal UserPrincipal caller) {

        log.debug("GET /api/v1/shifts/my/upcoming - caller={}", caller.getUsername());

        List<EntityData> shifts = genericEntityService
                .findByPayloadFieldInDateRange(
                        SHIFT_TYPE,
                        "employeeId",
                        caller.getUserId().toString(),
                        "shiftDate",
                        LocalDate.now(),
                        LocalDate.now().plusDays(60),
                        PageRequest.of(0, 100),   // covers ~3 months of daily shifts
                        caller)
                .getContent()
                .stream()
                .filter(e -> {
                    String s = e.getPayload().path("shiftStatus").asText("");
                    return !s.equals("COMPLETED") && !s.equals("CANCELLED");
                })
                .toList();

        return ResponseEntity.ok(shifts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manager view of a specific employee's shifts
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/shifts/employee/{employeeId}
     * Returns shifts for a specific employee (manager-facing endpoint used by employee detail view).
     */
    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER', 'HR_MANAGER', 'BUSINESS_OWNER') or #caller.userId == #employeeId")
    public ResponseEntity<List<EntityData>> getShiftsForEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal UserPrincipal caller) {

        log.debug("GET /api/v1/shifts/employee/{} - caller={}", employeeId, caller.getUsername());

        List<EntityData> shifts = genericEntityService
                .findByPayloadField(
                        SHIFT_TYPE,
                        "employeeId",
                        employeeId.toString(),
                        PageRequest.of(0, 50),
                        caller)
                .getContent();

        return ResponseEntity.ok(shifts);
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
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER', 'HR_MANAGER', 'BUSINESS_OWNER')")
    public ResponseEntity<Page<HrController.ScheduleEntryResponse>> getSchedule(
            @PathVariable UUID storeLocationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal caller) {

        log.info("Schedule query: location={}, {}->{}, requester={}",
                storeLocationId, startDate, endDate, caller.getUsername());

        generalService.enforceLocationScope(caller, storeLocationId.toString());

        Page<EntityData> shifts = entityService.findByPayloadFieldInDateRange(
                SHIFT_TYPE,
                "storeLocationId", storeLocationId.toString(),
                "shiftDate", startDate, endDate,
                pageable, caller);

        Page<HrController.ScheduleEntryResponse> enriched = shifts
                .map(shift -> generalService.enrichShiftWithEmployee(shift, caller));

        return ResponseEntity.ok(enriched);
    }
}