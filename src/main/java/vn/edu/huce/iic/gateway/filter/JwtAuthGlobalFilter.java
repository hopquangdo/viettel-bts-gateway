package vn.edu.huce.iic.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import vn.edu.huce.iic.gateway.config.GatewayProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    private static final String BEARER = "Bearer ";
    private static final String CLAIM_LOAI = "loai";
    private static final String LOAI_ACCESS = "access";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_FULL_ACCESS = "fa";
    private static final String CLAIM_PERMISSIONS = "han";
    private static final String CLAIM_SCOPE_VERSION = "sv";

    private final SecretKey key;
    private final List<PathPattern> publicPaths;
    private final List<PathPattern> blockedPaths;
    private final List<AccessRule> accessRules;
    private final boolean requireScope;

    public JwtAuthGlobalFilter(GatewayProperties properties) {
        String secret = properties.jwt() == null ? null : properties.jwt().secret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("gateway.jwt.secret (JWT_SECRET) is required");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.publicPaths = properties.publicPaths().stream().map(parser::parse).toList();
        this.blockedPaths = properties.blockedPaths().stream().map(parser::parse).toList();
        this.requireScope = properties.rules().requireScope();
        this.accessRules = properties.rules().rules().stream()
                .map(rule -> new AccessRule(parser.parse(rule.path()), rule.methods(), rule.anyRole(), rule.anyHan()))
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        PathContainer path = request.getPath().pathWithinApplication();
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        if (matches(blockedPaths, path)) {
            return reject(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài nguyên.");
        }
        boolean open = HttpMethod.OPTIONS.equals(request.getMethod()) || matches(publicPaths, path);
        if (!open) {
            String failure = verify(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), request);
            if (failure != null) {
                log.debug("Reject {} {}: {}", request.getMethod(), path.value(), failure);
                if ("insufficient scope".equals(failure)) {
                    return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
                }
                return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Yêu cầu đăng nhập hoặc token không hợp lệ.");
            }
        }
        ServerHttpRequest forwarded = request.mutate().header(REQUEST_ID_HEADER, requestId).build();
        return chain.filter(exchange.mutate().request(forwarded).build());
    }

    private String verify(String authorization, ServerHttpRequest request) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER)) {
            return "missing bearer token";
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorization.substring(BEARER.length()).trim()).getPayload();
            if (!LOAI_ACCESS.equals(claims.get(CLAIM_LOAI, String.class))) return "not an access token";
            AccessRule rule = accessRules.stream()
                    .filter(candidate -> candidate.path().matches(request.getPath().pathWithinApplication()))
                    .filter(candidate -> candidate.methods().isEmpty()
                            || candidate.methods().stream().anyMatch(method -> method.equalsIgnoreCase(request.getMethod().name())))
                    .findFirst().orElse(null);
            if (rule == null) return null;
            String role = claims.get(CLAIM_ROLE, String.class);
            List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);
            boolean fullAccess = Boolean.TRUE.equals(claims.get(CLAIM_FULL_ACCESS, Boolean.class));
            boolean hasScope = claims.get(CLAIM_SCOPE_VERSION) != null && role != null;
            if (!hasScope) return requireScope ? "missing scope" : null;
            boolean roleAllowed = rule.anyRole().isEmpty() || rule.anyRole().stream().anyMatch(value -> value.equalsIgnoreCase(role));
            boolean permissionAllowed = fullAccess || rule.anyHan().isEmpty()
                    || (permissions != null && rule.anyHan().stream().anyMatch(permissions::contains));
            return roleAllowed && permissionAllowed ? null : "insufficient scope";
        } catch (JwtException | IllegalArgumentException e) {
            return e.getClass().getSimpleName();
        }
    }

    private record AccessRule(PathPattern path, List<String> methods, List<String> anyRole, List<String> anyHan) {}

    private static boolean matches(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String type, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value() + ",\"isSuccess\":false,\"message\":\"" + message
                + "\",\"errors\":{\"type\":\"" + type + "\"}}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() { return -100; }
}