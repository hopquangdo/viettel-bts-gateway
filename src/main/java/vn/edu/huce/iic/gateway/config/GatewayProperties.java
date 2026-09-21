package vn.edu.huce.iic.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Jwt jwt, List<String> publicPaths, List<String> blockedPaths, Rules rules) {

    public GatewayProperties(Jwt jwt, List<String> publicPaths, List<String> blockedPaths) {
        this(jwt, publicPaths, blockedPaths, new Rules(false, List.of()));
    }

    public GatewayProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        blockedPaths = blockedPaths == null ? List.of() : List.copyOf(blockedPaths);
        rules = rules == null ? new Rules(false, List.of()) : rules;
    }

    /** Khóa HS256 dùng chung với backend ({@code JWT_SECRET}). */
    public record Jwt(String secret) {
    }

    public record Rules(boolean requireScope, List<Rule> rules) {
        public Rules {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    public record Rule(String path, List<String> methods, List<String> anyRole, List<String> anyHan) {
        public Rule {
            methods = methods == null ? List.of() : List.copyOf(methods);
            anyRole = anyRole == null ? List.of() : List.copyOf(anyRole);
            anyHan = anyHan == null ? List.of() : List.copyOf(anyHan);
        }
    }
}