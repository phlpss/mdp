package com.coffeeshop;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

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
class BaristaWorkflowIT {

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
    private static final String BARISTA_EMAIL = "ci.test.barista@mdpcoffee.com";
    private static final String PASSWORD = "password";
    private static final String PW_HASH =
            "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi";

    private RequestSpecification hrSpec;
    private RequestSpecification baristaSpec;
    private String baristaId;
    private String baristaUserId;
    private String shiftId;
    private String leaveId1;
    private String leaveId3;

    @BeforeAll
    void init() {
        RestAssured.reset();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        seedHrManagerData();
    }

    private void seedHrManagerData() {
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
        if (hrSpec == null) return;
        deleteEntity("LeaveRequest", leaveId3);
        deleteEntity("LeaveRequest", leaveId1);
        deleteEntity("Shift", shiftId);
        deleteEntity("User", baristaUserId);
        deleteEntity("Employee", baristaId);
    }

    private void deleteEntity(String type, String id) {
        if (id == null) return;
        given().spec(hrSpec)
                .when()
                .delete("/api/v1/entities/" + type + "/" + id);
    }

    @Test
    @Order(1)
    void step1_hrLogin_returnsTokenWithHrManagerRole() {
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

        hrSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + token)
                .setContentType(JSON)
                .build();
    }

    @Test
    @Order(2)
    void step2_hrCreatesBaristaEmployee_returns201WithEmployeeId() {
        baristaId = given().spec(hrSpec)
                .body("""
                        {
                          "fullName":         "CI Test Barista",
                          "email":            "ci.test.barista@mdpcoffee.com",
                          "phone":            "+380671119000",
                          "emergencyContact": "+380671119001",
                          "role":             "BARISTA",
                          "salary":           1400,
                          "hireDate":         "2026-06-01",
                          "isActive":         true,
                          "locationId":       "10000000-0000-0000-0000-000000000001",
                          "ptoBalance":       20,
                          "ptoTotal":         20,
                          "sickBalance":      10,
                          "sickTotal":        10,
                          "holidayBalance":   10,
                          "holidayTotal":     10,
                          "password":         "password"
                        }
                        """)
                .when()
                .post("/api/v1/hr/employees")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("type", equalTo("Employee"))
                .body("payload.fullName", equalTo("CI Test Barista"))
                .body("payload.role", equalTo("BARISTA"))
                .extract().path("id");
    }

    @Test
    @Order(3)
    void step3_baristaLogin_returnsTokenWithBaristaRole() {
        var response = given()
                .contentType(JSON)
                .body("""
                        {"username":"%s","password":"%s"}
                        """.formatted(BARISTA_EMAIL, PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("roles", hasItem("BARISTA"))
                .extract().response();

        baristaUserId = response.path("userId"); // User entity UUID (for cleanup)

        baristaSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + response.path("token"))
                .setContentType(JSON)
                .build();
    }

    @Test
    @Order(4)
    void step4_baristaGetMe_returnsPersonalInfo() {
        given().spec(baristaSpec)
                .when().get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(baristaId))
                .body("fullName", equalTo("CI Test Barista"))
                .body("username", equalTo(BARISTA_EMAIL))
                .body("isActive", equalTo(true))
                .body("roles", hasItem("BARISTA"));
    }

    @Test
    @Order(5)
    void step5_baristaGetLeaveBalance_returnsDefaultValues() {
        // Annual(PTO)=20, Sick=10, Holiday=10, all unused
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/balance")
                .then()
                .statusCode(200)
                .body("[0].type", equalTo("Annual"))
                .body("[0].used", equalTo(0))
                .body("[0].total", equalTo(20))
                .body("[1].type", equalTo("Sick"))
                .body("[1].used", equalTo(0))
                .body("[1].total", equalTo(10))
                .body("[2].type", equalTo("Holiday"))
                .body("[2].used", equalTo(0))
                .body("[2].total", equalTo(10));
    }

    @Test
    @Order(6)
    void step6_baristaGetShiftHistory_emptyList() {
        given().spec(baristaSpec)
                .when().get("/api/v1/shifts/employee/" + baristaId)
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    @Test
    @Order(7)
    void step7_defaultDashboard_allZerosAndDefaultBalances() {
        // Upcoming Shifts: 0
        given().spec(baristaSpec)
                .when().get("/api/v1/shifts/my/upcoming")
                .then()
                .statusCode(200)
                .body("$", empty());

        // Leave requests board: empty
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/my")
                .then()
                .statusCode(200)
                .body("content", empty())
                .body("totalElements", equalTo(0));

        // Leave Balances: default (sum of all remaining = 20+10+10 = 40 in UI)
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/balance")
                .then()
                .statusCode(200)
                .body("[0].type", equalTo("Annual"))
                .body("[0].used", equalTo(0))
                .body("[0].total", equalTo(20))
                .body("[1].used", equalTo(0))
                .body("[2].used", equalTo(0));
    }

    @Test
    @Order(8)
    void step8_hrCreatesShiftForBarista_returns201() {
        LocalDate shiftDate = LocalDate.now().plusDays(7);
        shiftId = given().spec(hrSpec)
                .body("""
                        {
                          "employeeId":        "%s",
                          "storeLocationId":   "%s",
                          "shiftDate":         "%s",
                          "startTime":         "%sT08:00:00",
                          "endTime":           "%sT16:00:00",
                          "mealBreakMinutes":  30,
                          "restBreakMinutes":  15
                        }
                        """.formatted(baristaId, LOCATION_ID, shiftDate, shiftDate, shiftDate))
                .when()
                .post("/api/v1/hr/shifts")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("payload.employeeId", equalTo(baristaId))
                .body("payload.shiftDate", equalTo(shiftDate.toString()))
                .body("payload.shiftStatus", equalTo("SCHEDULED"))
                .extract().path("id");
    }

    @Test
    @Order(9)
    void step9_baristaSeesNewShift_dashboardAndShiftCalendar() {
        // Upcoming Shifts: 1
        given().spec(baristaSpec)
                .when().get("/api/v1/shifts/my/upcoming")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(shiftId))
                .body("[0].payload.shiftStatus", equalTo("SCHEDULED"));

        // Shift Calendar / My Shifts tab: 1 entry
        given().spec(baristaSpec)
                .when().get("/api/v1/shifts/employee/" + baristaId)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(shiftId));
    }

    @Test
    @Order(10)
    void step10_leaveWorkflow1_submitApprove_balanceDeducted() {
        // Mon–Wed, 3 working days, at least 35 days from now
        LocalDate start = LocalDate.now().plusDays(35)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(2); // Wednesday

        // 10a — Barista submits leave
        leaveId1 = given().spec(baristaSpec)
                .body("""
                        {
                          "leaveType": "PTO",
                          "startDate": "%s",
                          "endDate":   "%s",
                          "notes":     "CI test leave — workflow 1"
                        }
                        """.formatted(start, end))
                .when()
                .post("/api/v1/hr/leave")
                .then()
                .statusCode(201)
                .body("payload.leaveStatus", equalTo("PENDING"))
                .body("payload.employeeId", equalTo(baristaId))
                .body("payload.daysRequested", equalTo(3))
                .extract().path("id");

        // 10b — Barista sees new pending request on the leave board
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/my")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].id", equalTo(leaveId1))
                .body("content[0].payload.leaveStatus", equalTo("PENDING"));

        // 10c — HR approves
        given().spec(hrSpec)
                .body("""
                        {"leaveStatus":"APPROVED","reviewNote":"Approved in CI test"}
                        """)
                .when()
                .patch("/api/v1/hr/leave/" + leaveId1 + "/status")
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("APPROVED"))
                .body("payload.reviewedBy", equalTo(HR_EMP_ID));

        // 10d — Barista sees approved status on the board
        given().spec(baristaSpec)
                .when().get("/api/v1/hr/leave/" + leaveId1)
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("APPROVED"));

        // 10e — Leave balance updated: 3 PTO days deducted (used 0→3, total stays 20)
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/balance")
                .then()
                .statusCode(200)
                .body("[0].type", equalTo("Annual"))
                .body("[0].used", equalTo(3))
                .body("[0].total", equalTo(20))
                .body("[1].used", equalTo(0)) // Sick: unchanged
                .body("[2].used", equalTo(0)); // Holiday: unchanged
    }

    @Test
    @Order(11)
    void step11_leaveWorkflow2_invalidPastDate_422NoRequestCreated() {
        LocalDate pastDate = LocalDate.now().minusDays(7);

        // 11a — Submit with past start date → 422 UNPROCESSABLE_CONTENT
        given().spec(baristaSpec)
                .body("""
                        {
                          "leaveType": "PTO",
                          "startDate": "%s",
                          "endDate":   "%s",
                          "notes":     "CI invalid leave — past date"
                        }
                        """.formatted(pastDate, pastDate.plusDays(2)))
                .when()
                .post("/api/v1/hr/leave")
                .then()
                .statusCode(422);

        // 11b — No new leave request created (still exactly 1 from workflow 1)
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/my")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1));

        // 11c — Balance unchanged
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/balance")
                .then()
                .statusCode(200)
                .body("[0].used", equalTo(3))
                .body("[0].total", equalTo(20));
    }

    @Test
    @Order(12)
    void step12_leaveWorkflow3_submitReject_balanceUnchanged() {
        // Mon–Tue, 2 working days (65+ days away to avoid overlap with WF1)
        LocalDate start = LocalDate.now().plusDays(65)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(1); // Tuesday

        // 12a — Barista submits leave
        leaveId3 = given().spec(baristaSpec)
                .body("""
                        {
                          "leaveType": "PTO",
                          "startDate": "%s",
                          "endDate":   "%s",
                          "notes":     "CI test leave — workflow 3"
                        }
                        """.formatted(start, end))
                .when()
                .post("/api/v1/hr/leave")
                .then()
                .statusCode(201)
                .body("payload.leaveStatus", equalTo("PENDING"))
                .body("payload.daysRequested", equalTo(2))
                .extract().path("id");

        // 12b — Board: 2 leave requests total, the new one is PENDING
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/my")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(2));

        given().spec(baristaSpec)
                .when().get("/api/v1/hr/leave/" + leaveId3)
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("PENDING"));

        // 12c — HR rejects
        given().spec(hrSpec)
                .body("""
                        {"leaveStatus":"REJECTED","reviewNote":"Rejected in CI test"}
                        """)
                .when()
                .patch("/api/v1/hr/leave/" + leaveId3 + "/status")
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("REJECTED"));

        // 12d — Barista sees updated board: request is REJECTED
        given().spec(baristaSpec)
                .when().get("/api/v1/hr/leave/" + leaveId3)
                .then()
                .statusCode(200)
                .body("payload.leaveStatus", equalTo("REJECTED"));

        // 12e — Balance UNCHANGED (rejection does not deduct)
        given().spec(baristaSpec)
                .when().get("/api/v1/leaves/balance")
                .then()
                .statusCode(200)
                .body("[0].type", equalTo("Annual"))
                .body("[0].used", equalTo(3))   // same as after workflow 1
                .body("[0].total", equalTo(20))
                .body("[1].used", equalTo(0))
                .body("[2].used", equalTo(0));
    }
}
