package com.coffeeshop.web;

import com.coffeeshop.security.UserPrincipal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finance module controller for accounting, payroll, and reporting operations.
 *
 * Implements business rules from:
 * - UC-FIN1: Daily closing reports with discrepancy detection
 * - UC-AI2: Anomaly detection via discrepancy events
 * - UC-FIN2: Payroll calculation engine
 * - UC-AI3: Revenue forecasting
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
    private final ObjectMapper objectMapper;

    public FinanceController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a daily closing report with reconciliation.
     *
     * Business Rule (UC-FIN1):
     * 1. Sum all Transaction EntityData for the given store location and date
     * 2. Compare against actual cash/payment total reported by staff
     * 3. Calculate discrepancy = actualTotal - calculatedTotal
     * 4. If discrepancy exceeds threshold, publish anomaly event to Pub/Sub
     *    for the AI anomaly detector (UC-AI2)
     * 5. Persist as DailyClosingReport EntityData for audit trail
     *
     * Access: SHIFT_SUPERVISOR or STORE_MANAGER
     *
     * @param body Request body: { "storeLocationId": "...", "date": "2026-04-10", "actualTotal": 1234.56 }
     * @param caller Authenticated user
     * @return DailyClosingReport with 201 status
     */
    @PostMapping("/closing-report")
    @PreAuthorize("hasAnyRole('SHIFT_SUPERVISOR', 'STORE_MANAGER')")
    public ResponseEntity<Object> createClosingReport(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        String storeLocationId = body.get("storeLocationId").asText();
        String date = body.get("date").asText();
        double actualTotal = body.get("actualTotal").asDouble();

        log.info("Closing report: store={}, date={}, actualTotal={}, submittedBy={}",
                storeLocationId, date, actualTotal, caller.getUsername());

        // TODO UC-FIN1: Daily closing reconciliation
        // 1. Parse date and validate it's not a future date
        // 2. Query all Transaction EntityData with filters:
        //    - storeLocationId matches
        //    - transactionDate == date
        //    - status == COMPLETED
        // 3. Sum the transactionAmount field across all transactions
        //    calculatedTotal = SUM(transaction.transactionAmount)
        // 4. Calculate discrepancy:
        //    discrepancy = actualTotal - calculatedTotal
        // 5. Create DailyClosingReport EntityData with fields:
        //    - storeLocationId
        //    - date
        //    - calculatedTotal
        //    - actualTotal
        //    - discrepancy
        //    - submittedBy (from caller.getUserId())
        //    - status = "PENDING" (awaiting manager approval)
        // 6. Persist to entity_data table
        // 7. If ABS(discrepancy) > THRESHOLD (e.g., 100.0):
        //    a. Publish DISCREPANCY_DETECTED event to Pub/Sub with:
        //       - report ID, store, date, discrepancy amount
        //       - severity: "CRITICAL" if > 500, "WARNING" if 100-500
        //    b. For UC-AI2 anomaly detector to investigate
        // 8. Return created report with 201 status

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * Calculate payroll for a given period and optional store.
     *
     * Business Rule (UC-FIN2):
     * 1. Fetch all completed ShiftRecord EntityData in the period
     * 2. For each employee:
     *    a. If paymentType == HOURLY: salary = paidTime_minutes * hourlyRate / 60
     *    b. If paymentType == FIXED: salary = monthlySalary * (days_in_period / days_in_month)
     * 3. Add approved leave days at daily_rate
     * 4. Deduct any discipline fines or adjustments
     * 5. Return PayrollLedger as a preview (NOT persisted yet)
     *
     * This is a dry-run endpoint. Actual payroll is locked via POST /payroll/process
     *
     * Access: ACCOUNTANT only
     *
     * @param periodStart ISO-8601 date (e.g., 2026-04-01)
     * @param periodEnd ISO-8601 date (e.g., 2026-04-30)
     * @param storeLocationId Optional store UUID (if absent, calculate for all stores)
     * @param caller Authenticated user
     * @return PayrollLedger DTO with gross/net totals per employee
     */
    @GetMapping("/payroll/calculate")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<Object> calculatePayroll(
            @RequestParam String periodStart,
            @RequestParam String periodEnd,
            @RequestParam(required = false) UUID storeLocationId,
            @AuthenticationPrincipal UserPrincipal caller) {

        log.info("Payroll calculation: period={} to {}, store={}, requester={}",
                periodStart, periodEnd, storeLocationId, caller.getUsername());

        // TODO UC-FIN2: Payroll calculation engine
        // 1. Parse dates and validate: periodStart <= periodEnd
        // 2. Query ShiftRecord EntityData with filters:
        //    - if storeLocationId provided: filter by it (else all stores)
        //    - clockOutTime is not NULL
        //    - clockOutTime between periodStart and periodEnd
        // 3. Group by employeeId
        // 4. For each employee:
        //    a. Fetch Employee EntityData to get paymentType, hourlyRate, monthlySalary
        //    b. Sum paidTime_minutes from all shifts
        //    c. Calculate base salary based on paymentType
        //    d. Query LeaveRequest EntityData for APPROVED leaves in period
        //    e. Add leave_days * daily_rate
        //    f. Deduct any Discipline or Adjustment EntityData marked as "DEDUCTION"
        //    g. Calculate gross = base + leave - deductions
        //    h. Calculate net = gross - (tax % to be implemented)
        // 5. Aggregate into PayrollLedger DTO:
        //    {
        //      "periodStart": "2026-04-01",
        //      "periodEnd": "2026-04-30",
        //      "employees": [
        //        {
        //          "employeeId": "...",
        //          "name": "...",
        //          "baseSalary": 1234.56,
        //          "leaveEarnings": 100.00,
        //          "deductions": 50.00,
        //          "grossSalary": 1284.56,
        //          "netSalary": 1157.10
        //        }
        //      ],
        //      "totalGross": 50000.00,
        //      "totalNet": 45000.00
        //    }
        // 6. Return as preview only (not persisted)

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "PREVIEW");
        response.put("message", "Payroll calculation stub - implementation pending");

        return ResponseEntity.ok(response);
    }

    /**
     * Lock and persist the payroll for a given period (confirm/process the calculation).
     *
     * Business Rule (UC-FIN2 continuation):
     * After reviewing the payroll preview via GET /payroll/calculate, the ACCOUNTANT
     * calls this endpoint to commit the payroll to the PayrollRecord EntityData table.
     *
     * Access: ACCOUNTANT only
     *
     * @param body Request body: { "periodStart": "2026-04-01", "periodEnd": "2026-04-30", "storeLocationId": "..." }
     * @param caller Authenticated user
     * @return Confirmation with list of created PayrollRecord entity IDs
     */
    @PostMapping("/payroll/process")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<Object> processPayroll(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal caller) {

        String periodStart = body.get("periodStart").asText();
        String periodEnd = body.get("periodEnd").asText();
        String storeLocationId = body.has("storeLocationId") ? body.get("storeLocationId").asText() : null;

        log.info("Payroll processing: period={} to {}, store={}, submittedBy={}",
                periodStart, periodEnd, storeLocationId, caller.getUsername());

        // TODO UC-FIN2: Payroll persistence and workflow
        // 1. Repeat payroll calculation logic from GET /payroll/calculate
        // 2. For each employee's payroll line item, create a PayrollRecord EntityData
        // 3. Set status = "APPROVED" and approvedBy = caller.getUserId()
        // 4. Persist all PayrollRecord entities in a single transaction
        // 5. Publish PAYROLL_PROCESSED event to Pub/Sub with period and employee count
        // 6. Return list of created PayrollRecord entity IDs for audit trail

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * Retrieve AI-powered revenue forecast for a store.
     *
     * Business Rule (UC-AI3):
     * This endpoint delegates to a BigQuery ML remote function (linear/exponential
     * regression model) trained on historical transaction data to forecast revenue
     * for the specified date.
     *
     * In production, this calls:
     *   https://bigquery.googleapis.com/bigquery/v2/projects/{project}/queries
     * with a SQL query that invokes the forecast ML function.
     *
     * Access: STORE_MANAGER or BUSINESS_OWNER
     *
     * @param storeLocationId UUID of the store
     * @param forecastDate ISO-8601 date to forecast
     * @param caller Authenticated user
     * @return ForecastResponse with predicted revenue and confidence interval
     */
    @GetMapping("/analytics/forecast")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'BUSINESS_OWNER')")
    public ResponseEntity<Object> getForecast(
            @RequestParam UUID storeLocationId,
            @RequestParam String forecastDate,
            @AuthenticationPrincipal UserPrincipal caller) {

        log.info("Forecast request: store={}, date={}, requester={}",
                storeLocationId, forecastDate, caller.getUsername());

        // TODO UC-AI3: BigQuery ML forecasting
        // 1. Parse forecastDate and validate it's a future date
        // 2. Call BigQueryService.getForecast(storeLocationId, forecastDate)
        // 3. This would execute a BigQuery remote function query:
        //    SELECT
        //      forecast_date,
        //      predicted_revenue,
        //      prediction_interval_low,
        //      prediction_interval_high,
        //      model_version
        //    FROM `project.dataset.ml_forecast_transactions`(
        //      @store_id = 'storeLocationId',
        //      @forecast_date = 'forecastDate'
        //    )
        // 4. Return ForecastResponse DTO:
        //    {
        //      "storeLocationId": "...",
        //      "forecastDate": "2026-04-15",
        //      "predictedRevenue": 5234.50,
        //      "confidenceInterval": {
        //        "low": 4800.00,
        //        "high": 5650.00
        //      },
        //      "modelVersion": "v2_2026_q2",
        //      "accuracy": 0.92
        //    }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "STUB");
        response.put("message", "BigQuery ML forecast integration pending");

        return ResponseEntity.ok(response);
    }
}

