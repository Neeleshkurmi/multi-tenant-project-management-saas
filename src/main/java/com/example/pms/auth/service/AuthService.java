
package com.example.pms.auth.service;

import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.auth.dto.RegisterResponse;
import com.example.pms.tenant.entity.Tenant;
import com.example.pms.tenant.entity.TenantMembership;
import com.example.pms.tenant.entity.TenantRole;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import com.example.pms.tenant.repository.TenantRepository;
import com.example.pms.user.entity.AppUser;
import com.example.pms.user.repository.AppUserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/*AuthService -> this class applies the business logic and validation checks onto
*                the auth request
*/

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AppUserRepository appUserRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder) {

        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail =
                request.email().trim().toLowerCase(Locale.ROOT);

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        AppUser user = appUserRepository.save(
                new AppUser(normalizedEmail, passwordHash));

        Tenant tenant = tenantRepository.save(
                new Tenant(request.organizationName()));

        TenantMembership membership = membershipRepository.save(
                new TenantMembership(user, tenant, TenantRole.OWNER));

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                tenant.getId(),
                tenant.getName(),
                membership.getRole().name());
    }
}
