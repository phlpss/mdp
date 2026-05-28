package com.coffeeshop.metadata;

import java.util.List;

public record MetaAttribute(
        String name,
        String label,
        String dataType,
        boolean mandatory,
        boolean sensitive,
        boolean sortable,
        boolean filterable,
        boolean showInList,
        boolean showInForm,
        boolean readOnly,
        Integer min,
        Integer max,
        List<String> allowedValues,
        String placeholder,
        String hint,
        int order,
        String group
) {
}

