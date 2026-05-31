package xyz.aimcup.auction.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.domain.model.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Issues and verifies the HS256 JWTs used to authenticate the SPA and the GraphQL layer.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(AuctionProperties properties) {
        // Raw UTF-8 bytes of the configured secret (>= 32 bytes => valid for HS256).
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.getJwt().getExpirationMs();
    }

    public String issue(User user, boolean admin) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("ROLE_USER");
        if (admin) {
            roles.add("ROLE_ADMIN");
        }
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getOsuId()))
                .claim("username", user.getUsername())
                .claim("avatarUrl", user.getAvatarUrl())
                .claim("admin", admin)
                .claim("roles", List.copyOf(roles))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the token and maps it to a principal, or returns {@code null} if it is missing,
     * malformed or expired.
     */
    public AuthenticatedUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims claims = jws.getPayload();
            long osuId = Long.parseLong(claims.getSubject());
            boolean admin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
            @SuppressWarnings("unchecked")
            List<String> roleList = claims.get("roles", List.class);
            Set<String> roles = roleList == null ? Set.of() : new LinkedHashSet<>(roleList);
            return new AuthenticatedUser(
                    osuId,
                    claims.get("username", String.class),
                    claims.get("avatarUrl", String.class),
                    admin,
                    roles);
        } catch (Exception e) {
            log.debug("Rejected JWT: {}", e.toString());
            return null;
        }
    }

    /** Strips an optional {@code Bearer } prefix and parses. */
    public AuthenticatedUser parseHeader(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String token = headerValue.regionMatches(true, 0, "Bearer ", 0, 7)
                ? headerValue.substring(7).trim()
                : headerValue.trim();
        return parse(token);
    }
}
