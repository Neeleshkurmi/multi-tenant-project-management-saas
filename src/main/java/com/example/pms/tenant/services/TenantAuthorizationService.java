
package com.example.pms.tenant.services;

import com.example.pms.tenant.entity.TenantMembership;
import com.example.pms.tenant.entity.TenantRole;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class TenantAuthorizationService {

    private final TenantMembershipRepository membershipRepository;

    public TenantMembership requireMembership(
            Long userId,
            Long tenantId
    ) {
        return membershipRepository
                .findByUser_IdAndTenant_Id(userId, tenantId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "You are not a member of this organisation"
                        )
                );
    }

    public TenantMembership requireRole(
            Long userId,
            Long tenantId,
            TenantRole... allowedRoles
    ) {
        TenantMembership membership =
                requireMembership(userId, tenantId);

        boolean authorized = Arrays.asList(allowedRoles)
                .contains(membership.getRole());

        if (!authorized) {
            throw new AccessDeniedException(
                    "You do not have permission to perform this action"
            );
        }

        return membership;
    }
}
