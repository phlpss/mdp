# Metadata-Driven Coffee Shop Management Platform - MDP Bootstrap

A production-grade Spring Boot 3.x backend implementing a "Configuration over Code" architecture for a coffee shop management system.

## Architecture Overview

This system uses a **metadata-driven, generic data engine** where:
- All domain entities (Employee, Transaction, Shift, etc.) are stored in a single PostgreSQL table (`entity_data`) as JSON
- Entity schemas are defined in Neo4j as `MetaType` nodes, not as Java classes
- Validation, authorization, and business rules are derived from metadata at runtime
- No hardcoded domain entity classes exist in the codebase

### Key Design Principles

1. **Configuration over Code**: Entity schemas are defined in Neo4j, not compiled Java classes
2. **Metadata-First Validation**: All CRUD operations validate against MetaType schemas
3. **Asynchronous Analytics**: Pub/Sub events are published non-blocking for BigQuery streaming
4. **Location-Scoped Authorization**: All operations respect store location boundaries
5. **Field-Level Masking**: Sensitive fields (e.g., salary) are masked based on caller role

## Technology Stack

- **Java 17** with Spring Boot 3.x
- **PostgreSQL** with JSONB for entity payloads
- **Neo4j** for metadata schema definitions
- **Google Cloud Pub/Sub** for event streaming to BigQuery
- **Spring Security 6** with JWT (HS256) for stateless authentication
- **Flyway** for database migrations
- **Lombok** for boilerplate reduction

### Critical Dependencies

```xml
<!-- JSONB Support - Hibernate 6.x (not hibernate-types-60 which targets Hibernate 5) -->
<io.hypersistence:hypersistence-utils-hibernate-63>3.7.0</io.hypersistence>

<!-- JWT Token Generation -->
<com.auth0:java-jwt>4.4.0</com.auth0>

<!-- GCP Pub/Sub -->
<com.google.cloud:spring-cloud-gcp-starter-pubsub>4.8.1</com.google.cloud>
```

## Project Structure

```
com.coffeeshop/
├── config/                    # Configuration classes
│   └── SecurityConfig.java    # Spring Security + JWT setup
├── metadata/                  # Neo4j in-memory models
│   ├── MetaAttribute.java     # Single attribute definition
│   ├── MetaType.java          # Entity type schema
│   ├── MetaRelationship.java  # Relationship definition
│   └── MetadataCacheService.java  # Loads/caches metadata at startup
├── operational/               # Core data layer
│   ├── EntityData.java        # JPA entity with JSONB payload
│   ├── EntityDataRepository.java
│   └── GenericEntityService.java  # CRUD + masking + Pub/Sub
├── validation/                # Validation engine
│   └── ValidationEngineService.java  # Schema validation rules
├── security/                  # JWT authentication
│   ├── Role.java
│   ├── SecurityConstants.java
│   ├── UserPrincipal.java     # Custom UserDetails
│   ├── JwtTokenProvider.java
│   └── JwtAuthFilter.java
├── web/                       # REST controllers
│   ├── GenericEntityController.java   # CRUD endpoints + idempotency
│   ├── AuthController.java    # Login endpoint
│   ├── HrController.java      # HR operations (stubs)
│   ├── FinanceController.java # Finance operations (stubs)
│   ├── AdminController.java   # Admin operations
│   └── GlobalExceptionHandler.java
├── analytics/                 # Event publishing
│   ├── PubSubPublisherService.java   # Async event publishing
│   └── BigQueryEventListener.java     # Placeholder for consumers
└── exception/                 # Custom exceptions
    ├── UnknownEntityTypeException.java
    ├── ValidationException.java
    ├── EntityNotFoundException.java
    └── IdempotencyConflictException.java
```

## Database Schema

### PostgreSQL: entity_data Table

```sql
CREATE TABLE entity_data (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type       VARCHAR(100) NOT NULL,          -- e.g., "Employee", "Transaction"
    payload    JSONB NOT NULL,                 -- All fields specific to type
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_entity_data_type ON entity_data (type);
CREATE INDEX idx_entity_data_payload ON entity_data USING gin (payload);
```

### Neo4j: Metadata Schema Example

```cypher
CREATE (empType:MetaType {name: "Employee"})
CREATE (idAttr:MetaAttribute {name: "employeeId", dataType: "STRING", mandatory: true})
CREATE (nameAttr:MetaAttribute {name: "firstName", dataType: "STRING", mandatory: true})
CREATE (salaryAttr:MetaAttribute {name: "salary", dataType: "DECIMAL", mandatory: false, sensitive: true})

CREATE (empType)-[:HAS_ATTRIBUTE]->(idAttr)
CREATE (empType)-[:HAS_ATTRIBUTE]->(nameAttr)
CREATE (empType)-[:HAS_ATTRIBUTE]->(salaryAttr)
```

## Configuration

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/coffeeshop
    username: postgres
    password: password
  
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: password

coffeeshop:
  jwt:
    secret: your-256-bit-secret-key-at-least-32-chars
    expiration-ms: 86400000  # 24 hours
  
  pubsub:
    topic-id: coffeeshop-entity-events
    project-id: your-gcp-project
```

### Environment Variables

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/coffeeshop
DATABASE_USER=postgres
DATABASE_PASSWORD=password

NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=password

JWT_SECRET=your-super-secret-key-change-in-production-at-least-32-characters-long
GCP_PROJECT_ID=your-gcp-project-id
PUBSUB_TOPIC_ID=coffeeshop-entity-events
```

## REST API Endpoints

### Authentication

```
POST /api/v1/auth/login
Body: { "username": "barista1", "password": "password" }
Response: { "token": "eyJhbGci...", "expiresIn": 86400 }
```

### Generic Entity CRUD

```
POST   /api/v1/entities/{type}           # Create (with idempotency support)
GET    /api/v1/entities/{type}           # List with pagination
GET    /api/v1/entities/{type}/{id}      # Get single
PUT    /api/v1/entities/{type}/{id}      # Update
DELETE /api/v1/entities/{type}/{id}      # Delete (HR_MANAGER/IT_SPECIALIST only)
```

### HR Operations

```
PATCH  /api/v1/hr/leave/{id}/status     # Approve/reject leave
POST   /api/v1/hr/clock-in               # Clock in (with conflict detection)
POST   /api/v1/hr/clock-out              # Clock out
GET    /api/v1/hr/schedule/{storeId}     # Get schedule
```

### Finance Operations

```
POST   /api/v1/finance/closing-report         # Create daily closing report
GET    /api/v1/finance/payroll/calculate      # Preview payroll
POST   /api/v1/finance/payroll/process        # Lock payroll
GET    /api/v1/finance/analytics/forecast     # AI-powered revenue forecast
```

### Admin Operations

```
POST   /api/v1/admin/metadata/reload    # Reload metadata from Neo4j (IT_SPECIALIST only)
```

## Security & Authorization

### Roles

| Role | Access Level | Use Case |
|------|---|---|
| BARISTA | Operational | Make coffee, clock in/out |
| WAITER | Operational | Serve customers, take orders |
| CASHIER | Operational | Process payments, manage register |
| CLEANER | Operational | Maintain store, clean up |
| SHIFT_SUPERVISOR | Supervisory | Oversee shift, approve leave |
| STORE_MANAGER | Supervisory | Manage store operations, finances |
| HR_MANAGER | Administrative | Manage employees, schedules |
| ACCOUNTANT | Administrative | Payroll, reporting, auditing |
| MARKETING | Administrative | Promotions, analytics |
| BUSINESS_OWNER | Executive | Strategic decisions, forecasting |
| IT_SPECIALIST | System | System administration, metadata reload |

### JWT Token Structure

```json
{
  "iss": null,
  "iat": 1712721234,
  "exp": 1712807634,
  "userId": "00000000-0000-0000-0000-000000000001",
  "username": "barista1",
  "roles": ["BARISTA"],
  "storeLocationId": "00000000-0000-0000-0000-100000000001"
}
```

### Field Masking Rules

Sensitive fields (defined in `MetaType.sensitiveFields`) are masked (`***MASKED***`) unless:
1. Caller has role `STORE_MANAGER` or `ACCOUNTANT`, OR
2. Caller is viewing their own record (employeeId matches userId)

Example: Employee salary is masked for BARISTA but visible to STORE_MANAGER and to the employee themselves.

## Validation Engine

The `ValidationEngineService` performs comprehensive validation:

### Data Type Validation
- STRING, INTEGER, DECIMAL, BOOLEAN, DATE (ISO-8601), ENUM

### Mandatory Field Checking
- All fields marked `mandatory: true` must be present and non-null

### Range Constraints
- **Strings**: min/max apply to length
- **Numbers**: min/max apply to value

### ENUM Validation
- Value must be in `allowedValues` list

### Relationship Validation
- Referenced entities must exist and match expected type

Example validation error response:

```json
{
  "error": "VALIDATION_FAILED",
  "violations": [
    "Mandatory field 'firstName' is missing or null",
    "Field 'salary' must be a decimal number, got double",
    "Field 'salary' must be at least 0, got -100"
  ],
  "status": 422
}
```

## Idempotency

POST endpoints support idempotent operations via `Idempotency-Key` header:

```bash
curl -X POST \
  -H "Idempotency-Key: 12345-67890" \
  -H "Authorization: Bearer {token}" \
  -d '{"firstName": "John"}' \
  http://localhost:8080/api/v1/entities/Employee
```

- Same key + same payload = returns cached response (201)
- Same key + different payload = returns 409 Conflict
- Keys expire after 24 hours

## Event Publishing

### Pub/Sub Events

After any successful write operation, an event is published asynchronously to Google Cloud Pub/Sub:

```json
{
  "eventId": "12345678-1234-1234-1234-123456789012",
  "eventType": "CREATED",
  "entityType": "Employee",
  "entityId": "00000000-0000-0000-0000-000000000001",
  "timestamp": "2026-04-10T12:34:56Z",
  "payload": { ... }
}
```

**Non-blocking**: Publishing failures do NOT interrupt the request. Events retry with exponential backoff (max 3 attempts).

### BigQuery Integration

The `BigQueryEventListener` is a placeholder for production Cloud Functions or Dataflow pipelines that:
1. Consume events from Pub/Sub
2. Stream to BigQuery for analytics
3. Execute ML models for anomaly detection (UC-AI2) and forecasting (UC-AI3)

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 13+
- Neo4j 4.4+
- Google Cloud account with Pub/Sub enabled

### Local Setup

1. **Start PostgreSQL & Neo4j**

```bash
docker run -d -e POSTGRES_PASSWORD=password -p 5432:5432 postgres:15
docker run -d -e NEO4J_AUTH=neo4j/password -p 7687:7687 -p 7474:7474 neo4j:4.4
```

2. **Create Neo4j Metadata** (via Neo4j Browser at http://localhost:7474)

```cypher
CREATE (empType:MetaType {name: "Employee"})
CREATE (txnType:MetaType {name: "Transaction"})
```

3. **Build & Run**

```bash
mvn clean package
java -jar target/mdp-0.0.1-SNAPSHOT.jar
```

4. **Obtain JWT Token**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"barista1","password":"password"}'
```

5. **Create an Entity**

```bash
curl -X POST http://localhost:8080/api/v1/entities/Employee \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "emp-001",
    "firstName": "John",
    "lastName": "Doe",
    "role": "BARISTA"
  }'
```

### Docker Build

```bash
docker build -t coffeeshop-mdp:latest .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/coffeeshop \
  -e NEO4J_URI=bolt://neo4j:7687 \
  -e JWT_SECRET=your-secret \
  -e GCP_PROJECT_ID=your-project \
  coffeeshop-mdp:latest
```

## Implementation Status

### ✅ Completed

- [x] Metadata-driven entity data model (PostgreSQL + Neo4j)
- [x] Generic CRUD operations with validation
- [x] JWT authentication (HS256)
- [x] Role-based authorization (@PreAuthorize)
- [x] Field-level masking based on roles
- [x] Pub/Sub event publishing (async, non-blocking)
- [x] Idempotent POST operations
- [x] Global exception handling
- [x] Database migrations (Flyway)
- [x] Docker support

### 🚧 TODO - Business Logic Implementation

The following controllers have full method signatures and Javadoc but require implementation:

- [ ] **HrController**: Leave approval, clock-in/out with dual-location conflict detection, schedule retrieval
- [ ] **FinanceController**: Daily closing reconciliation, payroll calculation, AI forecasting
- [ ] **MetadataCacheService.loadMetaTypes()**: Neo4j Cypher queries for metadata loading
- [ ] **ValidationEngineService.validateRelationshipRefs()**: Referential integrity checks
- [ ] **BigQueryService**: ML forecasting integration with BigQuery remote functions
- [ ] **User lookup**: Replace hardcoded in-memory users with EntityData-based user management

### 🚧 TODO - Production Enhancements

- [ ] Implement password hashing (BCrypt) for production users
- [ ] Add OAuth2 / Social login integration
- [ ] Implement audit logging for all entity changes
- [ ] Add request rate limiting and DDoS protection
- [ ] Implement transaction-level locking for concurrent updates
- [ ] Add caching layer (Redis) for frequently accessed entities
- [ ] Implement GraphQL layer for complex queries
- [ ] Add OpenAPI/Swagger documentation
- [ ] Implement health checks and metrics collection
- [ ] Set up distributed tracing (Jaeger/Zipkin)

## Testing

### Example Test Scenarios

1. **Create Employee & Retrieve with Masking**
   ```bash
   # Create employee with salary
   POST /api/v1/entities/Employee { "firstName": "Jane", "salary": 50000 }
   
   # As BARISTA - salary should be masked
   GET /api/v1/entities/Employee/{id} -> { "firstName": "Jane", "salary": "***MASKED***" }
   
   # As STORE_MANAGER - salary visible
   GET /api/v1/entities/Employee/{id} -> { "firstName": "Jane", "salary": 50000 }
   ```

2. **Dual-Location Conflict Detection**
   ```bash
   # Clock in at Store A
   POST /api/v1/hr/clock-in { "employeeId": "...", "storeLocationId": "store-a" }
   -> 201 Created
   
   # Try to clock in at Store B (should fail)
   POST /api/v1/hr/clock-in { "employeeId": "...", "storeLocationId": "store-b" }
   -> 409 Conflict
   ```

3. **Validation Errors**
   ```bash
   POST /api/v1/entities/Employee { "firstName": "" }  # Missing required fields
   -> 422 Unprocessable Entity with violation list
   ```

## Error Codes

| Code | HTTP Status | Description |
|------|---|---|
| UNKNOWN_TYPE | 404 | Entity type not found in metadata |
| VALIDATION_FAILED | 422 | Schema validation failed |
| NOT_FOUND | 404 | Entity not found |
| FORBIDDEN | 403 | Insufficient permissions |
| DUPLICATE_KEY | 409 | Idempotency key conflict |
| INTERNAL_ERROR | 500 | Unexpected server error |

## Support & Documentation

- **SRS**: See Section 1-9 in bootstrap specification
- **Architecture**: Configuration over Code pattern
- **API Docs**: Generated via Spring Boot Actuator (/actuator/*)
- **Security**: JWT + Role-Based Access Control (RBAC)

---

**Last Updated**: 2026-04-10
**Version**: 0.0.1-SNAPSHOT

