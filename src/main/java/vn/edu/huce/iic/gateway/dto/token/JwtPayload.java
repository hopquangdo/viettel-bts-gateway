package vn.edu.huce.iic.gateway.dto.token;

import java.util.List;

public record JwtPayload(String subject, String type, String role, boolean fullAccess,
                         List<String> permissions, Object scopeVersion) {
}