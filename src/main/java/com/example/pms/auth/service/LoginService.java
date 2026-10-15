
package com.example.pms.auth.service;

import com.example.pms.auth.dto.LoginRequest;
import com.example.pms.auth.dto.LoginResponse;
import com.example.pms.tenant.entity.TenantMembership;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import com.example.pms.user.entity.AppUser;
import com.example.pms.user.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class LoginService {

    private final AppUserRepository appUserRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long tokenLifetimeSeconds;

    public LoginService(
            AppUserRepository appUserRepository,
            TenantMembershipRepository tenantMembershipRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.expiration-seconds:3600}") long tokenLifetimeSeconds) {
        this.appUserRepository = appUserRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenLifetimeSeconds = tokenLifetimeSeconds;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()
                || !passwordEncoder.matches(
                request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        List<TenantMembership> memberships =
                tenantMembershipRepository.findAllByUser_Id(user.getId());

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pms-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(tokenLifetimeSeconds))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(jwsHeader, claims)
        ).getTokenValue();

        List<LoginResponse.TenantInfo> tenants = memberships.stream()
                .map(membership -> new LoginResponse.TenantInfo(
                        membership.getTenant().getId(),
                        membership.getTenant().getName(),
                        membership.getRole().name()))
                .toList();

        return new LoginResponse(
                token,
                "Bearer",
                tokenLifetimeSeconds,
                user.getId(),
                user.getEmail(),
                tenants);
    }
}
