package xyz.aimcup.auction.security;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates GraphQL traffic on both transports and exposes the principal to resolvers via the
 * GraphQL context under {@link AuthContext#KEY}.
 *
 * <ul>
 *   <li><b>HTTP</b> — reads the {@code Authorization: Bearer} header on each request.</li>
 *   <li><b>WebSocket</b> — reads the token from the {@code connection_init} payload once and remembers
 *       it for the lifetime of the socket, so every subscription frame on that socket is attributed
 *       to the same verified user.</li>
 * </ul>
 *
 * Subscriptions are intentionally open to anonymous spectators (a public auction page is viewable by
 * anyone); only authenticated principals are attributed, and mutations that actually change state
 * (bidding, organizer control) re-check authorization in the resolvers and services.
 */
@Component
@RequiredArgsConstructor
public class GraphQlAuthInterceptor implements WebSocketGraphQlInterceptor {

    private final JwtService jwtService;
    private final Map<String, AuthenticatedUser> sessionUsers = new ConcurrentHashMap<>();

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        AuthenticatedUser user = resolveUser(request);
        if (user != null) {
            request.configureExecutionInput((executionInput, builder) ->
                    builder.graphQLContext(Map.of(AuthContext.KEY, user)).build());
        }
        return chain.next(request);
    }

    private AuthenticatedUser resolveUser(WebGraphQlRequest request) {
        if (request instanceof WebSocketGraphQlRequest ws) {
            return sessionUsers.get(ws.getSessionInfo().getId());
        }
        return jwtService.parseHeader(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo sessionInfo,
                                                       Map<String, Object> connectionInitPayload) {
        Object token = firstNonNull(
                connectionInitPayload.get("Authorization"),
                connectionInitPayload.get("authorization"),
                connectionInitPayload.get("authToken"));
        AuthenticatedUser user = token == null ? null : jwtService.parseHeader(token.toString());
        if (user != null) {
            sessionUsers.put(sessionInfo.getId(), user);
        }
        return Mono.empty();
    }

    @Override
    public void handleConnectionClosed(WebSocketSessionInfo sessionInfo, int statusCode,
                                       Map<String, Object> connectionInitPayload) {
        sessionUsers.remove(sessionInfo.getId());
    }

    /** Exposes the per-socket user so the subscription resolver can register presence. */
    public AuthenticatedUser userForSession(String sessionId) {
        return sessionUsers.get(sessionId);
    }

    private static Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
