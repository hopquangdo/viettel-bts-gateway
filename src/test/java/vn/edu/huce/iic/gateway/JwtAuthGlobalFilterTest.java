package vn.edu.huce.iic.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-1234";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(new GatewayProperties(
            new GatewayProperties.Jwt(SECRET),
            List.of("/api/v1/xac-thuc/dang-nhap", "/uploads/**"),
            List.of("/mcp/**")));
        private final JwtAuthGlobalFilter scopedFilter = new JwtAuthGlobalFilter(new GatewayProperties(
            new GatewayProperties.Jwt(SECRET),
            List.of("/api/v1/xac-thuc/dang-nhap", "/uploads/**"),
            List.of("/mcp/**"),
            new GatewayProperties.Rules(false, List.of(new GatewayProperties.Rule(
                "/api/v1/admin/**", List.of("GET"), List.of(), List.of("QUAN_LY_QUYEN"))))));

    private static String token(String loai, long ttlMs) {
        return Jwts.builder().subject("u1").claim("loai", loai)
                .expiration(new Date(System.currentTimeMillis() + ttlMs)).signWith(KEY).compact();
    }

    private static String scopedToken(String role, boolean fullAccess, String... permissions) {
        return Jwts.builder().subject("u1").claim("loai", "access")
                .claim("role", role).claim("fa", fullAccess).claim("sv", 1)
                .claim("han", List.of(permissions))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(KEY).compact();
    }

    private record Result(HttpStatus status, boolean forwarded, String requestIdSeenByBackend) {
    }

    private Result call(HttpMethod method, String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .header("X-Request-Id", "forged");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        ServerWebExchange exchange = MockServerWebExchange.from(builder);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            seen.set(e);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        String forwardedId = seen.get() == null ? null : seen.get().getRequest().getHeaders().getFirst("X-Request-Id");
        return new Result((HttpStatus) exchange.getResponse().getStatusCode(), seen.get() != null, forwardedId);
    }

    @Test
    void validAccessTokenIsForwardedWithFreshRequestId() {
        Result r = call(HttpMethod.GET, "/api/v1/hop-dong", "Bearer " + token("access", 60_000));
        assertThat(r.forwarded()).isTrue();
        assertThat(r.requestIdSeenByBackend()).isNotEqualTo("forged").isNotBlank();
    }

    @Test
    void missingTokenIsRejected() {
        Result r = call(HttpMethod.GET, "/api/v1/hop-dong", null);
        assertThat(r.forwarded()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredRefreshAndForgedTokensAreRejected() {
        assertThat(call(HttpMethod.GET, "/api/v1/x", "Bearer " + token("access", -1_000)).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/v1/x", "Bearer " + token("refresh", 60_000)).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/v1/x", "Bearer abc.def.ghi").status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicPathsAndPreflightSkipAuth() {
        assertThat(call(HttpMethod.POST, "/api/v1/xac-thuc/dang-nhap", null).forwarded()).isTrue();
        assertThat(call(HttpMethod.GET, "/uploads/a/b.png", null).forwarded()).isTrue();
        assertThat(call(HttpMethod.OPTIONS, "/api/v1/hop-dong", null).forwarded()).isTrue();
    }

    @Test
    void blockedPathsReturn404EvenWithValidToken() {
        Result r = call(HttpMethod.POST, "/mcp", "Bearer " + token("access", 60_000));
        assertThat(r.forwarded()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void configuredScopeRuleReturnsForbiddenWithoutPermission() {
        Result r = callWith(scopedFilter, HttpMethod.GET, "/api/v1/admin/users",
                "Bearer " + scopedToken("STAFF", false));
        assertThat(r.forwarded()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void configuredScopeRuleForwardsMatchingPermission() {
        Result r = callWith(scopedFilter, HttpMethod.GET, "/api/v1/admin/users",
                "Bearer " + scopedToken("ADMIN", false, "QUAN_LY_QUYEN"));
        assertThat(r.forwarded()).isTrue();
    }

    private Result callWith(JwtAuthGlobalFilter selectedFilter, HttpMethod method, String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .header("X-Request-Id", "forged");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        ServerWebExchange exchange = MockServerWebExchange.from(builder);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            seen.set(e);
            return Mono.empty();
        };
        selectedFilter.filter(exchange, chain).block();
        String forwardedId = seen.get() == null ? null : seen.get().getRequest().getHeaders().getFirst("X-Request-Id");
        return new Result((HttpStatus) exchange.getResponse().getStatusCode(), seen.get() != null, forwardedId);
    }
}
