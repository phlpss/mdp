package com.coffeeshop;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end integration test for the HR Manager role
 * (iryna.lysenko@mdpcoffee.com).
 * <p>
 * Walks one HR manager through the capabilities they own in the system:
 * <ol>
 *   <li>Employee lifecycle — create, view, update, deactivate, delete</li>
 *   <li>Shift management — schedule and cancel</li>
 *   <li>Clock in / clock out — activating a planned shift and completing it</li>
 *   <li>Leave management — view all requests, approve (balance deducted),
 *       reject (balance untouched)</li>
 * </ol>
 * Each step asserts the observable HTTP outcome, and the steps build on one
 * another in order so the test reads as a single realistic workflow.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gcp.pubsub.enabled=false",
                "spring.flyway.baseline-on-migrate=false"
        }
)
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrManagerWorkflowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4")
                    .withDatabaseName("coffeeshop")
                    .withUsername("postgres")
                    .withPassword("pass");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private static final String LOCATION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String HR_EMP_ID = "20000000-0000-0000-0000-000000000004";
    private static final String HR_EMAIL = "iryna.lysenko@mdpcoffee.com";
    private static final String EMP_EMAIL = "ci.test.hr.employee@mdpcoffee.com";
    private static final String PASSWORD = "password";
    private static final String PW_HASH = "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi";

    private RequestSpecification hrSpec;
    private RequestSpecification employeeSpec;

    private String employeeId;
    private String employeeUserId;
    private String futureShiftId;
    private String todayShiftId;
    private String approvedLeaveId;
    private String rejectedLeaveId;

    @BeforeAll
    void init() {
        RestAssured.reset();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        seedHrManager();
    }

    /**
     * Seeds the location plus the HR manager's Employee and User records.
     */
    private void seedHrManager() {
        jdbc.execute("""
                INSERT INTO entity_data (id, type, payload) VALUES
                ('10000000-0000-0000-0000-000000000001', 'StoreLocation',
                 '{"storeName":"MDP Coffee — Downtown","address":"15 Rynok Square, Lviv 79008",
                   "phone":"+380322610001","isActive":true}')
                ON CONFLICT (id) DO NOTHING
                """);

        jdbc.execute("""
                INSERT INTO entity_data (id, type, payload) VALUES
                ('20000000-0000-0000-0000-000000000004', 'Employee',
                 '{"fullName":"Iryna Lysenko","email":"iryna.lysenko@mdpcoffee.com",
                   "phone":"+380671110004","role":"HR_MANAGER","salary":2200,
                   "hireDate":"2024-08-10","isActive":true,
                   "locationId":"10000000-0000-0000-0000-000000000001",
                   "ptoBalance":25,"ptoTotal":25,
                   "sickBalance":15,"sickTotal":15,
                   "holidayBalance":14,"holidayTotal":14}')
                ON CONFLICT (id) DO NOTHING
                """);

        jdbc.execute("""
                INSERT INTO entity_data (id, type, payload) VALUES
                ('30000000-0000-0000-0000-000000000004', 'User',
                 '{"username":"iryna.lysenko@mdpcoffee.com",
                   "passwordHash":"%s",
                   "roles":["HR_MANAGER"],
                   "employeeId":"20000000-0000-0000-0000-000000000004",
                   "isActive":true}')
                ON CONFLICT (id) DO NOTHING
                """.formatted(PW_HASH));
    }

    @AfterAll
    void cleanup() {
        deleteQuietly("LeaveRequest", rejectedLeaveId);
        deleteQuietly("LeaveRequest", approvedLeaveId);
        deleteQuietly("Shift", todayShiftId);
        deleteQuietly("Shift", futureShiftId);
        deleteQuietly("User", employeeUserId);
        deleteQuietly("Employee", employeeId);
    }

    private void deleteQuietly(String type, String id) {
        if (hrSpec == null || id == null) return;
        given().spec(hrSpec).when().delete("/api/v1/entities/" + type + "/" + id);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void step01_hrLogin_returnsTokenWithHrManagerRole() {
        String token = given()
                .contentType(JSON)
                .body("""
                        {"username":"%s","password":"%s"}
                        """.formatted(HR_EMAIL, PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("roles", hasItem("HR_MANAGER"))
                .extract().path("token");

        hrSpec = bearerSpec(token);
    }

    // ── Employee lifecycle: create ──────────────────────────────────────────────

    @Test
    @Order(2)
    void step02_hrCreatesEmployee_returns201WithLinkedAccount() {
        employeeId = given().spec(hrSpec)
                .body("""
                        {
                          "fullName":         "CI HR Test Employee",
                          "email":            "%s",
                          "phone":            "+380671119500",
                          "emergencyContact": "+380671119501",
                          "role":             "BARISTA",
                          "salary":           1400,
                          "hireDate":         "2026-06-01",
                          "isActive":         true,
                          "locationId":       "%s",
                          "ptoBalance":       20,
                          "ptoTotal":         20,
                          "sickBalance":      10,
                          "sickTotal":        10,
                          "holidayBalance":   10,
                          "holidayTotal":     10,
                          "password":         "%s"
                        }
                        """.formatted(EMP_EMAIL, LOCATION_ID, PASSWORD))
                .when()
                .post("/api/v1/hr/employees")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("type", equalTo("Employee"))
                .body("payload.fullName", equalTo("CI HR Test Employee"))
                .body("payload.role", equalTo("BARISTA"))
                .extract().path("id");
    }

    // ── Employee lifecycle: view all ────────────────────────────────────────────

    @Test
    @Order(3)
    void step03_hrViewsAllEmployees_listIncludesNewEmployee() {
        given().spec(hrSpec)
                .when().get("/api/v1/entities/Employee?size=200")
                .then()
                .statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("content.id", hasItem(employeeId));
    }

    // ── Employee lifecycle: update ──────────────────────────────────────────────

    @Test
    @Order(4)
    void step04_hrUpdatesEmployee_phoneAndSalaryChanged() {
        given().spec(hrSpec)
                .body("""
                        {"phone":"+380671119599","salary":1600}
                        """)
                .when()
                .patch("/api/v1/entities/Employee/" + employeeId)
                .then()
                .statusCode(200)
                .body("payload.phone", equalTo("+380671119599"))
                .body("payload.salary", equalTo(1600))
                .body("payload.fullName", equalTo("CI HR Test Employee")); // untouched
    }

    // ── Shift management: schedule + cancel ─────────────────────────────────────

    @Test
    @Order(5)
    void step05_hrSchedulesFutureShift_returns201Scheduled() {
        LocalDate shiftDate = LocalDate.now().plusDays(14);
        futureShiftId = given().spec(hrSpec)
                .body(shiftBody(shiftDate))
                .when()
                .post("/api/v1/hr/shifts")
                .then()
                .statusCode(201)
                .body("payload.employeeId", equalTo(employeeId))
                .body("payload.shiftDate", equalTo(shiftDate.toString()))
                .body("payload.shiftStatus", equalTo("SCHEDULED"))
                .extract().path("id");
    }

    @Test
    @Order(6)
    void step06_hrCancelsScheduledShift_returnsCancelled() {
        given().spec(hrSpec)
                .when()
                .patch("/api/v1/hr/shifts/" + futureShiftId + "/cancel")
                .then()
                .statusCode(200)
                .body("payload.shiftStatus", equalTo("CANCELLED"));
    }

    // ── Clock in / clock out ────────────────────────────────────────────────────

    @Test
    @Order(7)
    void step07_hrSchedulesTodayShift_forClockInOut() {
        LocalDate today = LocalDate.now();
        todayShiftId = given().spec(hrSpec)
                .body(shiftBody(today))
                .when()
                .post("/api/v1/hr/shifts")
                .then()
                .statusCode(201)
                .body("payload.shiftStatus", equalTo("SCHEDULED"))
                .extract().path("id");
    }

    @Test
    @Order(8)
    void step08_clockIn_activatesScheduledShift() {
        given().spec(hrSpec)
                .body("""
                        {"employeeId":"%s","storeLocationId":"%s"}
                        """.formatted(employeeId, LOCATION_ID))
                .when()
                .post("/api/v1/hr/clock-in")
                .then()
                .statusCode(201)
                .body("id", equalTo(todayShiftId))                 // existing shift reused, not a new one
                .body("payload.shiftStatus", equalTo("ACTIVE"))
                .body("payload.clockInTime", notNullValue());
    }

    @Test
    @Order(9)
    void step09_clockOut_completesShiftWithPaidMinutes() {
        given().spec(hrSpec)
                .body("""
                        {"shiftId":"%s","mealBreakMinutes":0,"restBreakMinutes":0}
                        """.formatted(todayShiftId))
                .when()
                .post("/api/v1/hr/clock-out")
                .then()
                .statusCode(200)
                .body("payload.shiftStatus", equalTo("COMPLETED"))
                .body("payload.clockOutTime", notNullValue())
                .body("payload.paidMinutes", notNullValue());
    }

    // ── Leave management: subordinate submits, HR reviews ───────────────────────

    @Test
    @Order(10)
    void step10_employeeLogin_forLeaveSubmission() {
        Response response = given()
                .contentType(JSON)
                .body("""
                        {"username":"%s","password":"%s"}
                        """.formatted(EMP_EMAIL, PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("roles", hasItem("BARISTA"))
                .extract().response();

        employeeUserId = response.path("userId");
        employeeSpec = bearerSpec(response.path("token"));
    }

    @Test
    @Order(11)
    void step11_employeeSubmitsLeave_pending() {
        // Mon–Wed → 3 working days, comfortably in the future.
        LocalDate start = LocalDate.now().plusDays(40)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(2);

        approvedLeaveId = given().spec(employeeSpec)
                .body(leaveBody(start, end))
                .when()
                .post("/api/v1/hr/leave")
                .then()
                .statusCode(201)
                .body("payload.leaveStatus", equalTo("PENDING"))
                .body("payload.employeeId", equalTo(employeeId))
                .body("payload.daysRequested", equalTo(3))
                .extract().path("id");
    }

    @Test
    @Order(12)
    void step12_hrViewsAllLeaveRequests_includesPendingRequest() {
        given().spec(hrSpec)
                .when().get("/api/v1/hr/leave?size=200")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(approvedLeaveId));
    }

    @Test
    @Order(13)
    void step13_hrApprovesLeave_balanceDeducted() {
        given().spec(hrSpec)
                .body("""
                        {"leaveStatus":"APPROVED","reviewNote":"Approved in HR IT"}
                        """)
                .when()
                .patch("/api/v1/hr/leave/" + approvedLeaveId + "/status")
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("APPROVED"))
                .body("payload.reviewedBy", equalTo(HR_EMP_ID));

        // 3 PTO days deducted: Annual used 0 → 3, total unchanged.
        given().spec(hrSpec)
                .when().get("/api/v1/leaves/balance?employeeId=" + employeeId)
                .then()
                .statusCode(200)
                .body("[0].type", equalTo("Annual"))
                .body("[0].used", equalTo(3))
                .body("[0].total", equalTo(20));
    }

    @Test
    @Order(14)
    void step14_hrRejectsSecondLeave_balanceUnchanged() {
        // A different week so it never overlaps the approved request.
        LocalDate start = LocalDate.now().plusDays(80)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(1); // Mon–Tue → 2 working days

        rejectedLeaveId = given().spec(employeeSpec)
                .body(leaveBody(start, end))
                .when()
                .post("/api/v1/hr/leave")
                .then()
                .statusCode(201)
                .body("payload.daysRequested", equalTo(2))
                .extract().path("id");

        given().spec(hrSpec)
                .body("""
                        {"leaveStatus":"REJECTED","reviewNote":"Rejected in HR IT"}
                        """)
                .when()
                .patch("/api/v1/hr/leave/" + rejectedLeaveId + "/status")
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("REJECTED"));

        // Rejection does not touch the balance: Annual still 3 used.
        given().spec(hrSpec)
                .when().get("/api/v1/leaves/balance?employeeId=" + employeeId)
                .then()
                .statusCode(200)
                .body("[0].used", equalTo(3))
                .body("[0].total", equalTo(20));
    }

    // ── Employee lifecycle: deactivate ──────────────────────────────────────────

    @Test
    @Order(15)
    void step15_hrDeactivatesEmployee_loginIsBlocked() {
        // Deactivate both the Employee record and the login account.
        given().spec(hrSpec)
                .body("""
                        {"isActive":false}
                        """)
                .when()
                .patch("/api/v1/entities/Employee/" + employeeId)
                .then()
                .statusCode(200)
                .body("payload.isActive", equalTo(false));

        given().spec(hrSpec)
                .body("""
                        {"isActive":false}
                        """)
                .when()
                .patch("/api/v1/entities/User/" + employeeUserId)
                .then()
                .statusCode(200)
                .body("payload.isActive", equalTo(false));

        // The deactivated account can no longer authenticate.
        given()
                .contentType(JSON)
                .body("""
                        {"username":"%s","password":"%s"}
                        """.formatted(EMP_EMAIL, PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    // ── Employee lifecycle: delete ──────────────────────────────────────────────

    @Test
    @Order(16)
    void step16_hrDeletesEmployee_thenGoneFromSystem() {
        // Remove dependent records first, then the account and the employee.
        given().spec(hrSpec).when()
                .delete("/api/v1/entities/LeaveRequest/" + rejectedLeaveId)
                .then().statusCode(204);
        given().spec(hrSpec).when()
                .delete("/api/v1/entities/LeaveRequest/" + approvedLeaveId)
                .then().statusCode(204);
        given().spec(hrSpec).when()
                .delete("/api/v1/entities/Shift/" + todayShiftId)
                .then().statusCode(204);
        given().spec(hrSpec).when()
                .delete("/api/v1/entities/Shift/" + futureShiftId)
                .then().statusCode(204);
        given().spec(hrSpec).when()
                .delete("/api/v1/entities/User/" + employeeUserId)
                .then().statusCode(204);
        given().spec(hrSpec).when()
                .delete("/api/v1/entities/Employee/" + employeeId)
                .then().statusCode(204);

        // The employee no longer exists.
        given().spec(hrSpec)
                .when().get("/api/v1/entities/Employee/" + employeeId)
                .then()
                .statusCode(404);

        // Null the ids so @AfterAll does not attempt to re-delete them.
        rejectedLeaveId = approvedLeaveId = todayShiftId = futureShiftId = null;
        employeeUserId = employeeId = null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private RequestSpecification bearerSpec(String token) {
        return new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + token)
                .setContentType(JSON)
                .build();
    }

    private String shiftBody(LocalDate date) {
        return """
                {
                  "employeeId":       "%s",
                  "storeLocationId":  "%s",
                  "shiftDate":        "%s",
                  "startTime":        "%sT08:00:00",
                  "endTime":          "%sT16:00:00",
                  "mealBreakMinutes": 30,
                  "restBreakMinutes": 15
                }
                """.formatted(employeeId, LOCATION_ID, date, date, date);
    }

    private String leaveBody(LocalDate start, LocalDate end) {
        return """
                {
                  "leaveType": "PTO",
                  "startDate": "%s",
                  "endDate":   "%s",
                  "notes":     "HR IT leave request"
                }
                """.formatted(start, end);
    }
}