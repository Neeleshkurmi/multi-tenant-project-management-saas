package com.example.pms.tenant.repository;

import com.example.pms.tenant.entity.TenantInvitation;
import com.example.pms.tenant.entity.TenantInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantInvitationRepository
        extends JpaRepository<TenantInvitation, Long> {

    Optional<TenantInvitation> findByTokenHash(String tokenHash);

    boolean existsByTenant_IdAndInvitedEmailAndStatus(
            Long tenantId,
            String invitedEmail,
            TenantInvitationStatus status
    );

    List<TenantInvitation> findAllByTenant_IdAndStatus(
            Long tenantId,
            TenantInvitationStatus status
    );
}