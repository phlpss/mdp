package com.coffeeshop.operational;

import com.coffeeshop.exception.EntityNotFoundException;
import com.coffeeshop.metadata.MetaType;
import com.coffeeshop.metadata.MetadataCacheService;
import com.coffeeshop.security.Role;
import com.coffeeshop.security.UserPrincipal;
import com.coffeeshop.validation.ValidationEngineService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for generic entity operations (CRUD).
 *
 * Enforces:
 * 1. Validation against MetaType schema before any write
 * 2. Field-level masking based on caller's role before returning data
 * 3. Asynchronous Pub/Sub event publishing after successful writes
 * 4. Audit logging of all operations
 */
@Slf4j
@Service
@Transactional
public class GenericEntityService {
    private final EntityDataRepository entityDataRepository;
    private final MetadataCacheService metadataCacheService;
    private final ValidationEngineService validationEngineService;
//    private final PubSubPublisherService pubSubPublisherService;
    private final ObjectMapper objectMapper;

    public GenericEntityService(EntityDataRepository entityDataRepository, MetadataCacheService metadataCacheService, ValidationEngineService validationEngineService, ObjectMapper objectMapper) {
        this.entityDataRepository = entityDataRepository;
        this.metadataCacheService = metadataCacheService;
        this.validationEngineService = validationEngineService;
//        this.pubSubPublisherService = pubSubPublisherService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new entity with the given type and payload.
     *
     * @param type The entity type (e.g., "Employee", "Transaction")
     * @param payload The entity payload
     * @param caller The authenticated user
     * @return The created entity with masking applied
     */
    public EntityData create(String type, JsonNode payload, UserPrincipal caller) {
        log.info("Creating entity of type '{}' by user: {}", type, caller.getUsername());

        validationEngineService.validate(type, payload);

        EntityData entity = EntityData.builder().type(type).payload(payload).build();
        EntityData saved = entityDataRepository.save(entity);
        log.info("Entity created: type='{}', id='{}'", type, saved.getId());

        // todo: Publish event asynchronously (non-blocking)
//        pubSubPublisherService.publishEntityEvent("CREATED", type, saved.getId(), payload);

        return applyFieldMask(type, saved, caller);
    }

    /**
     * Retrieve paginated entities of a given type.
     *
     * @param type The entity type
     * @param pageable Pagination parameters
     * @param caller The authenticated user
     * @return Page of entities with masking applied
     */
    public Page<EntityData> findAll(String type, Pageable pageable, UserPrincipal caller) {
        log.debug("Fetching page: type='{}', page={}", type, pageable.getPageNumber());
        metadataCacheService.getType(type);
        return entityDataRepository.findByType(type, pageable)
                .map(entity -> applyFieldMask(type, entity, caller));
    }

    /**
     * Retrieve a single entity by ID.
     *
     * @param type The entity type
     * @param id The entity UUID
     * @param caller The authenticated user
     * @return The entity with masking applied
     */
    public EntityData findById(String type, UUID id, UserPrincipal caller) {
        log.debug("Fetching entity: type='{}', id='{}'", type, id);

        metadataCacheService.getType(type);

        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) {
            throw new EntityNotFoundException(type, id);
        }

        return applyFieldMask(type, entity, caller);
    }

    public EntityData findById(String type, UUID id) {
        log.debug("Fetching entity: type='{}', id='{}'", type, id);

        metadataCacheService.getType(type);

        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) {
            throw new EntityNotFoundException(type, id);
        }

        return entity;
    }

    /**
     * Update an entity.
     *
     * @param type The entity type
     * @param id The entity UUID
     * @param payload The updated payload
     * @param caller The authenticated user
     * @return The updated entity with masking applied
     */
    public EntityData update(String type, UUID id, JsonNode payload, UserPrincipal caller) {
        log.info("Updating entity: type='{}', id='{}' by user: {}", type, id, caller.getUsername());
        validationEngineService.validate(type, payload);
        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) throw new EntityNotFoundException(type, id);
        entity.setPayload(payload);
        EntityData updated = entityDataRepository.save(entity);
        log.info("Entity updated: type='{}', id='{}'", type, id);
//      pubSubPublisherService.publishEntityEvent("UPDATED", type, updated.getId(), payload);
        return applyFieldMask(type, updated, caller);
    }

    public void delete(String type, UUID id, UserPrincipal caller) {
        log.info("Deleting entity: type='{}', id='{}' by user: {}", type, id, caller.getUsername());

        EntityData entity = entityDataRepository.findByTypeAndId(type, id);
        if (entity == null) {
            throw new EntityNotFoundException(type, id);
        }

        entityDataRepository.deleteById(id);
        log.info("Entity deleted: type='{}', id='{}'", type, id);

//        todo
//        pubSubPublisherService.publishEntityEvent("DELETED", type, id, entity.getPayload());
    }

    /**
     * Returns a page of entities filtered by a single JSONB payload field.
     *
     * Replaces the in-memory stream filter pattern that was used as a temporary
     * workaround (loading all rows then filtering in Java).
     *
     * Uses a native PostgreSQL JSONB ->> query pushed all the way to the database,
     * so pagination (LIMIT / OFFSET) is applied before any data leaves Postgres.
     *
     * Example: findByPayloadField("LeaveRequest", "employeeId", uuid, pageable, caller)
     * → SELECT * FROM entity_data WHERE type = 'LeaveRequest'
     *     AND payload->>'employeeId' = '<uuid>'
     */
    public Page<EntityData> findByPayloadField(String type,
                                               String fieldName,
                                               String fieldValue,
                                               Pageable pageable,
                                               UserPrincipal caller) {
        log.debug("Fetching entities by payload field: type='{}', {}='{}'",
                type, fieldName, fieldValue);

        metadataCacheService.getType(type); // validate type exists in Neo4j schema

        return entityDataRepository
                .findByTypeAndPayloadField(type, fieldName, fieldValue, pageable)
                .map(entity -> applyFieldMask(type, entity, caller));
    }

    public Optional<EntityData> findByPayloadField(UUID id, String fieldName) {
        UUID resultId = entityDataRepository.extractPayloadField(id, fieldName);
        return entityDataRepository.findById(resultId);
    }

    /**
     * Returns entities filtered by a JSONB field equality and a JSONB date field range.
     * Pushes the date range filter entirely to PostgreSQL — no in-memory filtering.
     * <p>
     * Example: findByPayloadFieldInDateRange("Shift", "storeLocationId", uuid,
     *              "shiftDate", startDate, endDate, pageable, caller)
     * → WHERE type = 'Shift'
     *     AND payload->>'storeLocationId' = '<uuid>'
     *     AND (payload->>'shiftDate')::date BETWEEN :startDate AND :endDate
     */
    public Page<EntityData> findByPayloadFieldInDateRange(String type,
                                                          String fieldName,
                                                          String fieldValue,
                                                          String dateField,
                                                          LocalDate startDate,
                                                          LocalDate endDate,
                                                          Pageable pageable,
                                                          UserPrincipal caller) {
        log.debug("Fetching entities by field+date range: type='{}', {}='{}', {}=[{} → {}]",
                type, fieldName, fieldValue, dateField, startDate, endDate);

        metadataCacheService.getType(type);

        return entityDataRepository
                .findByTypeAndPayloadFieldInDateRange(
                        type, fieldName, fieldValue, dateField, startDate, endDate, pageable)
                .map(entity -> applyFieldMask(type, entity, caller));
    }

    /**
     * Returns a page of entities filtered by up to two JSONB payload fields.
     * Either filter can be null — passing null skips that condition entirely
     * (the native query uses IS NULL OR ... to make each filter optional).
     *
     * Example: findByTwoPayloadFields("LeaveRequest", "leaveStatus", "PENDING",
     *                                  "leaveType", "PTO", pageable, caller)
     * → SELECT * FROM entity_data WHERE type = 'LeaveRequest'
     *     AND payload->>'leaveStatus' = 'PENDING'
     *     AND payload->>'leaveType'   = 'PTO'
     */
    public Page<EntityData> findByTwoPayloadFields(String type,
                                                   String fieldName1,
                                                   String fieldValue1,
                                                   String fieldName2,
                                                   String fieldValue2,
                                                   Pageable pageable,
                                                   UserPrincipal caller) {
        metadataCacheService.getType(type);

        boolean hasFilter1 = fieldName1 != null && fieldValue1 != null;
        boolean hasFilter2 = fieldName2 != null && fieldValue2 != null;

        Page<EntityData> page;

        if (hasFilter1 && hasFilter2) {
            page = entityDataRepository.findByTypeAndTwoPayloadFields(
                    type, fieldName1, fieldValue1, fieldName2, fieldValue2, pageable);
        } else if (hasFilter1) {
            page = entityDataRepository.findByTypeAndOnePayloadField(
                    type, fieldName1, fieldValue1, pageable);
        } else if (hasFilter2) {
            page = entityDataRepository.findByTypeAndOnePayloadField(
                    type, fieldName2, fieldValue2, pageable);
        } else {
            page = entityDataRepository.findByType(type, pageable);
        }

        return page.map(entity -> applyFieldMask(type, entity, caller));
    }

    // ── field masking (unchanged) ─────────────────────────────────────────────

    private EntityData applyFieldMask(String type, EntityData entity, UserPrincipal caller) {
        MetaType metaType = metadataCacheService.findType(type).orElse(null);
        if (metaType == null
                || metaType.sensitiveFields() == null
                || metaType.sensitiveFields().isEmpty()) {
            return entity;
        }

        boolean canViewSensitive = caller.hasAnyRole(
                Role.STORE_MANAGER,
                Role.ACCOUNTANT);
        if (canViewSensitive) return entity;

        // Owner check — employee can see their own sensitive fields
        JsonNode payloadNode;
        try {
            payloadNode = objectMapper.readTree(String.valueOf(entity.getPayload()));
        } catch (Exception e) {
            return entity; // unparseable payload — return as-is, let caller handle
        }

        boolean isOwner = payloadNode.has("employeeId")
                && payloadNode.get("employeeId").asText()
                .equals(caller.getUserId().toString());
        if (isOwner) return entity;

        // Mask sensitive fields
        EntityData masked = new EntityData();
        masked.setId(entity.getId());
        masked.setType(entity.getType());
        masked.setCreatedAt(entity.getCreatedAt());
        masked.setUpdatedAt(entity.getUpdatedAt());

        ObjectNode maskedPayload = objectMapper.createObjectNode();
        payloadNode.properties().iterator().forEachRemaining(entry -> {
            if (metaType.sensitiveFields().contains(entry.getKey())) {
                maskedPayload.put(entry.getKey(), "***MASKED***");
            } else {
                maskedPayload.set(entry.getKey(), entry.getValue());
            }
        });

        masked.setPayload(maskedPayload);
        return masked;
    }
}
