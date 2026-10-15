
package com.example.pms.auth.dto;

public record RegisterResponse(
        Long userId,
        String email,
        Long tenantId,
        String organizationName,
        String role
) {
}
