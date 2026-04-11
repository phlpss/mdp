package com.coffeeshop.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Custom UserDetails implementation carrying additional claims from JWT:
 * - userId: UUID of the authenticated user
 * - storeLocationId: UUID of the user's assigned store location
 *
 * Used by Spring Security to represent the authenticated principal throughout the request.
 */
@Getter
public class UserPrincipal implements UserDetails {
    private final UUID userId;
    private final String username;
    private final Set<Role> roles;
    private final UUID storeLocationId;

    public UserPrincipal(UUID userId, String username, Set<Role> roles, UUID storeLocationId) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.storeLocationId = storeLocationId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        // JWT doesn't use password-based authentication
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Check if this principal has a specific role.
     */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /**
     * Check if this principal has any of the specified roles.
     */
    public boolean hasAnyRole(Role... roles) {
        return Arrays.stream(roles).anyMatch(this.roles::contains);
    }
}

