package com.coffeeshop.metadata;

import java.util.List;

/**
 * Immutable record representing a single attribute in a MetaType schema.
 *
 * Example: For an Employee MetaType, an attribute might be:
 *   name: "salary"
 *   dataType: "DECIMAL"
 *   mandatory: true
 *   min: 0
 *   max: 1000000
 *   allowedValues: null (only populated for ENUM type)
 */
public record MetaAttribute(
        String name,
        String dataType,           // "STRING", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "ENUM"
        boolean mandatory,
        Integer min,               // min length for strings, min value for numbers
        Integer max,               // max length for strings, max value for numbers
        List<String> allowedValues // non-empty only when dataType = "ENUM"
) {
}

