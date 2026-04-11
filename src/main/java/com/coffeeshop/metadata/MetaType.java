package com.coffeeshop.metadata;

import java.util.List;

/**
 * Immutable record representing a domain entity type in the metadata schema.
 *
 * Each MetaType defines the structure and validation rules for all entities of that type.
 * Example: MetaType named "Employee" with attributes (id, firstName, lastName, salary, etc.)
 *
 * sensitiveFields are masked based on the caller's role and permissions.
 * For example, "Employee" type might have sensitiveFields = ["salary", "ssn"].
 */
public record MetaType(String name, List<MetaAttribute> attributes, List<String> sensitiveFields
                       // sensitiveFields - fields to mask based on caller role (e.g. ["salary"])
) {
}
