package com.coffeeshop.web;

import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
public class EmployeeShiftController {

    private static final String SHIFT_TYPE = "Shift";

    private final GenericEntityService genericEntityService;

    public EmployeeShiftController(GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
    }

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
                .findByPayloadField(
                        SHIFT_TYPE,
                        "employeeId",
                        caller.getUserId().toString(),
                        PageRequest.of(0, 20),
                        caller)
                .getContent();

        return ResponseEntity.ok(shifts);
    }
}