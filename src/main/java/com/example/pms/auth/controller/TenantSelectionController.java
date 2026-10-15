
package com.example.pms.auth.controller;

import com.example.pms.auth.dto.SelectTenantRequest;
import com.example.pms.auth.service.TenantSelectionService;
import com.example.pms.tenant.entity.TenantMembership;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TenantSelectionController {

    private final TenantSelectionService tenantSelectionService;
    private final JwtEncoder jwtEncoder;

    @Value("${security.jwt.expiration-seconds:3600}")
    private long tokenLifetimeSeconds;

    @PostMapping("/select-tenant")
    public TenantSelectionResponse selectTenant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelectTenantRequest request) {

        if (jwt == null) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Authentication is required");
        }

        Long userId = Long.valueOf(Objects.requireNonNull(jwt.getSubject()));

        TenantMembership membership =
                tenantSelectionService.validateMembership(
                        userId, request.tenantId());

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pms-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(tokenLifetimeSeconds))
                .subject(userId.toString())
                .claim("email", Objects.requireNonNull(jwt.getClaimAsString("email")))
                .claim("tenant_id", membership.getTenant().getId())
                .claim("role", membership.getRole().name())
                .build();

        String tenantAccessToken = jwtEncoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(),
                                claims))
                .getTokenValue();

        return new TenantSelectionResponse(
                tenantAccessToken,
                "Bearer",
                tokenLifetimeSeconds,
                membership.getTenant().getId(),
                membership.getTenant().getName(),
                membership.getRole().name());
    }

    public record TenantSelectionResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            Long tenantId,
            String organizationName,
            String role) {
    }
}
