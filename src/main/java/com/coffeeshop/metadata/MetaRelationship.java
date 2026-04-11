package com.coffeeshop.metadata;

/**
 * Immutable record representing a relationship between two entity types in the metadata graph.
 *
 * Example: A MetaRelationship might describe:
 *   name: "PROCESSED_BY"
 *   fromType: "Transaction"
 *   toType: "Employee"
 *   mandatory: false
 *
 * This indicates that a Transaction entity may have a field referencing an Employee ID.
 * The mandatory flag enforces referential integrity if true.
 */
public record MetaRelationship(String name,               // e.g. "PROCESSED_BY", "BASE_STORE", "HAS_EMPLOYEE"
                               String fromType,           // source entity type
                               String toType,             // target entity type
                               boolean mandatory          // enforces referential integrity
) {
}
