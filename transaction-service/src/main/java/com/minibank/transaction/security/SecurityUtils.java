package com.minibank.transaction.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Helpers for reading the authenticated caller from the security context.
 * The JWT filter stores the caller's accountId as the authentication credentials.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Account ID of the caller, or null if the caller has no bank account (e.g. ADMIN).
     */
    public static UUID currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getCredentials() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getCredentials().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isOwner(UUID accountId) {
        return accountId != null && accountId.equals(currentAccountId());
    }

    /**
     * Throws 403 unless the caller owns the account or is an ADMIN.
     */
    public static void requireOwnerOrAdmin(UUID accountId) {
        if (!isAdmin() && !isOwner(accountId)) {
            throw new AccessDeniedException("You do not have access to this account");
        }
    }
}
