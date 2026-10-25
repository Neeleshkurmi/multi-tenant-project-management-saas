package com.example.pms.tenant.repository;

import com.example.pms.tenant.entity.TenantInvitation;
import com.example.pms.tenant.entity.TenantInvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT i
    FROM TenantInvitation i
    WHERE i.tokenHash = :tokenHash
    """)
    Optional<TenantInvitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT i FROM TenantInvitation i
    WHERE i.tenant.id = :tenantId
      AND i.invitedEmail = :email
      AND i.status = :status
    """)
    Optional<TenantInvitation> findPendingForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("email") String email,
            @Param("status") TenantInvitationStatus status
    );

    List<TenantInvitation> findAllByTenant_IdOrderByCreatedAtDesc(Long tenantId);

}