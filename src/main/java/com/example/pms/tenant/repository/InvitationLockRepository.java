
package com.example.pms.tenant.repository;

import com.example.pms.tenant.entity.InvitationLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvitationLockRepository
        extends JpaRepository<InvitationLock, Long> {

    @Modifying
    @Query(value = """
        INSERT INTO invitation_locks (tenant_id, invited_email)
        VALUES (:tenantId, :email)
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    void createIfMissing(
            @Param("tenantId") Long tenantId,
            @Param("email") String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT l FROM InvitationLock l
        WHERE l.tenantId = :tenantId
          AND l.invitedEmail = :email
        """)
    Optional<InvitationLock> findForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("email") String email
    );
}
