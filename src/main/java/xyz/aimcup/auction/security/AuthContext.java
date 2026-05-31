package xyz.aimcup.auction.security;

import xyz.aimcup.auction.domain.exception.ForbiddenException;

/**
 * Conventions and helpers for reading the authenticated principal that the auth interceptor places
 * into the GraphQL context.
 */
public final class AuthContext {

    /** GraphQL context key the {@link AuthenticatedUser} is stored under. */
    public static final String KEY = "authUser";

    private AuthContext() {
    }

    /** Returns the user or throws {@link ForbiddenException} when unauthenticated. */
    public static AuthenticatedUser require(AuthenticatedUser user) {
        if (user == null) {
            throw new ForbiddenException("Authentication required");
        }
        return user;
    }

    /** Requires an authenticated user that also holds {@code ROLE_ADMIN}. */
    public static AuthenticatedUser requireAdmin(AuthenticatedUser user) {
        require(user);
        if (!user.admin() && !user.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("Administrator role required");
        }
        return user;
    }
}
