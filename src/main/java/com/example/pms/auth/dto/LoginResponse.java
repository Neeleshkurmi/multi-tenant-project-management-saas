package com.example.pms.auth.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email,
        List<TenantInfo> tenants
) {

    public record TenantInfo(
            Long tenantId,
            String organizationName,
            String role
    ) {
    }
}