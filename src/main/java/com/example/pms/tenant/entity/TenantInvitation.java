package com.example.pms.tenant.entity;

import com.example.pms.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
    name = "tenant_invitations",
    indexes = {
        @Index(
            name = "idx_invitation_tenant_email",
            columnList = "tenant_id, invited_email"
        ),
        @Index(
            name = "idx_invitation_expires_at",
            columnList = "expires_at"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "invited_email", nullable = false, length = 254)
    private String invitedEmail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private AppUser invitedBy;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public TenantInvitation(
            Tenant tenant,
            String invitedEmail,
            AppUser invitedBy,
            String tokenHash,
            TenantRole role,
            Instant expiresAt
    ) {
        this.tenant = tenant;
        this.invitedEmail = invitedEmail.trim().toLowerCase();
        this.invitedBy = invitedBy;
        this.tokenHash = tokenHash;
        this.role = role;
        this.expiresAt = expiresAt;
        this.status = TenantInvitationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void accept(Instant acceptedAt) {
        if (status != TenantInvitationStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending invitations can be accepted"
            );
        }

        this.status = TenantInvitationStatus.ACCEPTED;
        this.acceptedAt = Objects.requireNonNull(acceptedAt);
    }

    public void expire() {
        if (status == TenantInvitationStatus.PENDING) {
            this.status = TenantInvitationStatus.EXPIRED;
        }
    }

    public void revoke() {
        this.status = TenantInvitationStatus.REVOKED;
    }

    public void setExpiresAtForTesting(Instant expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }
}