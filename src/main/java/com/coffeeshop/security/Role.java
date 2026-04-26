package com.coffeeshop.security;

import lombok.Getter;

/**
 * Enumeration of all system roles in the metadata-driven coffee shop platform.
 * Organized by hierarchy:
 * <p>
 * Operational Roles (in-store): BARISTA, WAITER, CASHIER, CLEANER
 * Supervisory Roles: SHIFT_SUPERVISOR, STORE_MANAGER
 * Administrative Roles: HR_MANAGER, ACCOUNTANT, MARKETING
 * Executive Role: BUSINESS_OWNER
 * System Role: IT_SPECIALIST
 */
@Getter
public enum Role {
    // Operational roles
    BARISTA("BARISTA"),
    WAITER("WAITER"),
    CASHIER("CASHIER"),
    CLEANER("CLEANER"),

    // Supervisory roles
    SHIFT_SUPERVISOR("SHIFT_SUPERVISOR"),
    STORE_MANAGER("STORE_MANAGER"),

    // Administrative roles
    HR_MANAGER("HR_MANAGER"),
    ACCOUNTANT("ACCOUNTANT"),
    MARKETING("MARKETING"),

    // Executive
    BUSINESS_OWNER("BUSINESS_OWNER"),

    // System
    IT_SPECIALIST("IT_SPECIALIST");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String getAuthority() {
        return "ROLE_" + value;
    }
}

