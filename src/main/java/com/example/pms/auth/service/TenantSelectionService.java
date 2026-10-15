package com.example.pms.auth.service;

import com.example.pms.tenant.entity.TenantMembership;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSelectionService {

    private final TenantMembershipRepository membershipRepository;

    public TenantSelectionService(
            TenantMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public TenantMembership validateMembership(
            Long userId,
            Long tenantId) {

        return membershipRepository
                .findByUser_IdAndTenant_Id(userId, tenantId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "You do not have access to this organization"));
    }
}