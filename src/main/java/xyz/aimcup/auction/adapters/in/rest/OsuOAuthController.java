package xyz.aimcup.auction.adapters.in.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.aimcup.auction.adapters.out.osu.OsuOAuthClient;
import xyz.aimcup.auction.application.service.UserService;
import xyz.aimcup.auction.config.AuctionProperties;
import xyz.aimcup.auction.security.JwtService;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Drives the osu! authorization-code login. We implement the flow by hand (rather than Spring's
 * reactive {@code oauth2Login}) so the redirect URI exactly matches the one registered on the osu!
 * application and so we can mint our own JWT and hand it to the SPA.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OsuOAuthController {

    private static final String STATE_COOKIE = "osu_oauth_state";
    private static final String REDIRECT_COOKIE = "osu_oauth_redirect";

    private final OsuOAuthClient oauthClient;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuctionProperties properties;

    /** Step 1: bounce the browser to osu! for consent. */
    @GetMapping("/oauth2/authorize/osu")
    public Mono<Void> authorize(@RequestParam(defaultValue = "/") String redirect, ServerWebExchange exchange) {
        AuctionProperties.Osu osu = properties.getOsu();
        String state = UUID.randomUUID().toString();

        ServerHttpResponse response = exchange.getResponse();
        response.addCookie(shortLivedCookie(STATE_COOKIE, state));
        response.addCookie(shortLivedCookie(REDIRECT_COOKIE, redirect));

        String url = osu.getAuthorizationUri()
                + "?client_id=" + enc(osu.getClientId())
                + "&redirect_uri=" + enc(osu.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + enc(osu.getScopes())
                + "&state=" + enc(state);

        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url));
        return response.setComplete();
    }

    /** Step 2: osu! redirects back here with a code; exchange it, mint a JWT, bounce to the SPA. */
    @GetMapping("/oauth2/callback/osu")
    public Mono<Void> callback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String redirect = cookieValue(request, REDIRECT_COOKIE, "/");
        String expectedState = cookieValue(request, STATE_COOKIE, null);

        clearCookie(exchange.getResponse(), STATE_COOKIE);
        clearCookie(exchange.getResponse(), REDIRECT_COOKIE);

        if (error != null) {
            return redirectToSpa(exchange, redirect, null, "osu! login was cancelled");
        }
        if (code == null || expectedState == null || !expectedState.equals(state)) {
            return redirectToSpa(exchange, redirect, null, "Invalid OAuth state");
        }

        return oauthClient.exchangeCode(code)
                .flatMap(oauthClient::fetchMe)
                .flatMap(userService::upsert)
                .map(user -> jwtService.issue(user, userService.isAdmin(user.getOsuId())))
                .flatMap(token -> redirectToSpa(exchange, redirect, token, null))
                .onErrorResume(e -> {
                    log.error("osu! login failed", e);
                    return redirectToSpa(exchange, redirect, null, "Login failed");
                });
    }

    private Mono<Void> redirectToSpa(ServerWebExchange exchange, String redirect, String token, String error) {
        StringBuilder url = new StringBuilder(properties.getFrontendUrl())
                .append("/auth/callback?redirect=").append(enc(redirect));
        if (token != null) {
            url.append("&token=").append(enc(token));
        }
        if (error != null) {
            url.append("&error=").append(enc(error));
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.toString()));
        return response.setComplete();
    }

    private ResponseCookie shortLivedCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).path("/").maxAge(600).sameSite("Lax").build();
    }

    private void clearCookie(ServerHttpResponse response, String name) {
        response.addCookie(ResponseCookie.from(name, "").path("/").maxAge(0).build());
    }

    private String cookieValue(ServerHttpRequest request, String name, String fallback) {
        var cookie = request.getCookies().getFirst(name);
        return cookie == null ? fallback : cookie.getValue();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
