package com.coffeeshop.service;

import com.coffeeshop.exception.EntityNotFoundException;
import com.coffeeshop.operational.EntityData;
import com.coffeeshop.operational.GenericEntityService;
import com.coffeeshop.security.Role;
import com.coffeeshop.security.UserPrincipal;
import com.coffeeshop.web.HrController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Service
public class GeneralService {

    private static final String SHIFT_TYPE = "Shift";
    private static final String EMPLOYEE_TYPE = "Employee";

    private final GenericEntityService entityService;
    private final ObjectMapper objectMapper;

    public GeneralService(GenericEntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
     public String deriveShiftMode(JsonNode payload) {
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
     public HrController.ScheduleEntryResponse enrichShiftWithEmployee(EntityData shift, UserPrincipal caller) {
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
                employeePosition = ep.path("role").asString("Unknown");
            } catch (EntityNotFoundException e) {
                log.warn("Employee not found for shift {}: employeeId={}", shift.getId(), employeeId);
            }
        }

        return new HrController.ScheduleEntryResponse(
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
     public void validateNoShiftConflict(String employeeId, String shiftDate,
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
     public void enforceLocationScope(UserPrincipal caller, String targetLocationId) {
        if (caller.hasRole(Role.STORE_MANAGER)
                && caller.getStoreLocationId() != null
                && !caller.getStoreLocationId().toString().equals(targetLocationId)) {
            throw new AccessDeniedException(
                    "STORE_MANAGER is restricted to their assigned location");
        }
    }

     public JsonNode parsePayload(EntityData entity) {
        try {
            return objectMapper.readTree(String.valueOf(entity.getPayload()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Corrupt payload for entity " + entity.getId());
        }
    }

     public String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

     public String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required field: " + field);
        }
        return value.asString();
    }
}
