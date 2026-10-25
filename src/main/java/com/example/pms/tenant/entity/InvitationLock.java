package com.example.pms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "invitation_locks",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_invitation_lock_tenant_email",
        columnNames = {"tenant_id", "invited_email"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "invited_email", nullable = false, length = 254)
    private String invitedEmail;

    public InvitationLock(Long tenantId, String invitedEmail) {
        this.tenantId = tenantId;
        this.invitedEmail = invitedEmail.trim().toLowerCase();
    }
}