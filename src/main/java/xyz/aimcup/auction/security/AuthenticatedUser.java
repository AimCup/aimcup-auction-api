package xyz.aimcup.auction.security;

import java.util.Set;

/**
 * The authenticated principal carried in the GraphQL context for both HTTP and WebSocket transports.
 * Derived from a verified JWT.
 */
public record AuthenticatedUser(
        long osuId,
        String username,
        String avatarUrl,
        boolean admin,
        Set<String> roles
) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
