package com.coffeeshop.validation;

import com.coffeeshop.exception.ValidationException;
import com.coffeeshop.metadata.MetaAttribute;
import com.coffeeshop.metadata.MetaType;
import com.coffeeshop.metadata.MetadataCacheService;
import com.coffeeshop.operational.EntityDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Validation engine implementing the Interpreter pattern for schema validation.
 * <p>
 * This service validates entity payloads against their MetaType schema loaded from Neo4j.
 * It performs comprehensive validation including:
 * - Mandatory field checking
 * - Data type validation
 * - Range constraints (min/max)
 * - ENUM value validation
 * - Referential integrity for relationships
 * <p>
 * All validation errors are collected (not short-circuiting) to provide comprehensive
 * feedback to the caller.
 */
@Slf4j
@Service
public class ValidationEngineService {
    private final MetadataCacheService metadataCacheService;
    private final EntityDataRepository entityDataRepository;

    public ValidationEngineService(MetadataCacheService metadataCacheService,
                                   EntityDataRepository entityDataRepository) {
        this.metadataCacheService = metadataCacheService;
        this.entityDataRepository = entityDataRepository;
    }

    /**
     * Main validation entry point. Validates a payload against its MetaType schema.
     *
     * @param type The entity type name
     * @param payload The JSON payload to validate
     * @throws ValidationException if validation fails
     */
    public void validate(String type, JsonNode payload) {
        log.debug("Starting validation for entity type '{}' with payload keys: {}", type,
                payload.fieldNames().hasNext() ? "present" : "empty");

        MetaType metaType = metadataCacheService.getType(type);
        List<String> errors = new ArrayList<>();

        validateMandatoryFields(metaType, payload, errors);
        validateDataTypes(metaType, payload, errors);
        validateRangeConstraints(metaType, payload, errors);
        validateRelationshipRefs(type, metaType, payload, errors);

        if (!errors.isEmpty()) {
            log.warn("Validation failed for type '{}' with {} errors", type, errors.size());
            throw new ValidationException(errors);
        }

        log.debug("Validation passed for entity type '{}'", type);
    }

    /**
     * Validates that all mandatory attributes are present and non-null in the payload.
     */
    private void validateMandatoryFields(MetaType metaType, JsonNode payload, List<String> errors) {
        for (MetaAttribute attr : metaType.attributes()) {
            if (attr.mandatory()) {
                if (payload.get(attr.name()) == null || payload.get(attr.name()) instanceof NullNode) {
                    errors.add("Mandatory field '" + attr.name() + "' is missing or null");
                    log.trace("Mandatory field check failed for: {}", attr.name());
                }
            }
        }
    }

    /**
     * Validates that all present fields conform to their declared data types.
     * Supports: STRING, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM
     */
    private void validateDataTypes(MetaType metaType, JsonNode payload, List<String> errors) {
        for (MetaAttribute attr : metaType.attributes()) {
            JsonNode value = payload.get(attr.name());

            // Skip null values if not mandatory (already checked above)
            if (value == null || value instanceof NullNode) {
                continue;
            }

            switch (attr.dataType()) {
                case "STRING":
                    if (!value.isTextual()) {
                        errors.add("Field '" + attr.name() + "' must be a string, got " + value.getNodeType());
                    }
                    break;

                case "INTEGER":
                    if (!value.isIntegralNumber()) {
                        errors.add("Field '" + attr.name() + "' must be an integer, got " + value.getNodeType());
                    }
                    break;

                case "DECIMAL":
                    if (!value.isNumber()) {
                        errors.add("Field '" + attr.name() + "' must be a decimal number, got " + value.getNodeType());
                    }
                    break;

                case "BOOLEAN":
                    if (!value.isBoolean()) {
                        errors.add("Field '" + attr.name() + "' must be a boolean, got " + value.getNodeType());
                    }
                    break;

                case "DATE":
                    if (!value.isTextual()) {
                        errors.add("Field '" + attr.name() + "' must be a string (ISO-8601 date), got " + value.getNodeType());
                    } else {
                        try {
                            LocalDate.parse(value.asText());
                        } catch (DateTimeParseException e) {
                            errors.add("Field '" + attr.name() + "' must be a valid ISO-8601 date (YYYY-MM-DD), got '" +
                                    value.asText() + "'");
                        }
                    }
                    break;

                case "ENUM":
                    if (!value.isTextual()) {
                        errors.add("Field '" + attr.name() + "' must be a string (ENUM), got " + value.getNodeType());
                    } else if (attr.allowedValues() != null && !attr.allowedValues().isEmpty()) {
                        String valueStr = value.asText();
                        if (!attr.allowedValues().contains(valueStr)) {
                            errors.add("Field '" + attr.name() + "' has invalid enum value '" + valueStr +
                                    "'. Allowed values: " + attr.allowedValues());
                        }
                    }
                    break;

                default:
                    log.warn("Unknown data type: {}", attr.dataType());
            }
        }
    }

    /**
     * Validates that field values respect their min/max constraints.
     * For strings: constraints apply to length.
     * For numbers: constraints apply to value.
     */
    private void validateRangeConstraints(MetaType metaType, JsonNode payload, List<String> errors) {
        for (MetaAttribute attr : metaType.attributes()) {
            JsonNode value = payload.get(attr.name());

            if (value == null || value instanceof NullNode) {
                continue;
            }

            if ("STRING".equals(attr.dataType()) && value.isTextual()) {
                int length = value.asText().length();
                if (attr.min() != null && length < attr.min()) {
                    errors.add("Field '" + attr.name() + "' length must be at least " + attr.min() +
                            ", got " + length);
                }
                if (attr.max() != null && length > attr.max()) {
                    errors.add("Field '" + attr.name() + "' length must not exceed " + attr.max() +
                            ", got " + length);
                }
            } else if ("INTEGER".equals(attr.dataType()) && value.isIntegralNumber()) {
                long numValue = value.asLong();
                if (attr.min() != null && numValue < attr.min()) {
                    errors.add("Field '" + attr.name() + "' must be at least " + attr.min() +
                            ", got " + numValue);
                }
                if (attr.max() != null && numValue > attr.max()) {
                    errors.add("Field '" + attr.name() + "' must not exceed " + attr.max() +
                            ", got " + numValue);
                }
            } else if ("DECIMAL".equals(attr.dataType()) && value.isNumber()) {
                BigDecimal numValue = value.decimalValue();
                if (attr.min() != null && numValue.compareTo(BigDecimal.valueOf(attr.min())) < 0) {
                    errors.add("Field '" + attr.name() + "' must be at least " + attr.min() +
                            ", got " + numValue);
                }
                if (attr.max() != null && numValue.compareTo(BigDecimal.valueOf(attr.max())) > 0) {
                    errors.add("Field '" + attr.name() + "' must not exceed " + attr.max() +
                            ", got " + numValue);
                }
            }
        }
    }

    /**
     * Validates referential integrity for relationships.
     * If a field corresponds to a MetaRelationship target, verifies that the referenced
     * entity exists in PostgreSQL and is of the correct type.
     * <p>
     * Example: For a Transaction entity with processedByEmployeeId field referencing
     * an Employee, this method ensures the referenced Employee actually exists.
     */
    private void validateRelationshipRefs(String type, MetaType metaType, JsonNode payload, List<String> errors) {
        // TODO UC-REL-1: Implement relationship validation logic
        // For each MetaRelationship where this type is the source:
        // 1. Look for corresponding fields in the payload that match the pattern "{relationshipName}Id"
        // 2. Extract the UUID value and validate it exists in entity_data table
        // 3. Verify the referenced entity's type matches the relationship target type

        log.trace("Relationship validation stubbed for type '{}'", type);
    }
}


