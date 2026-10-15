
package com.example.pms.tenant.entity;

import com.example.pms.user.entity.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/* TenantMembership -> this class works as a bridge between a tenant and appUser
                       both the tenant and appUser can exist independently this class
                       provides the bridge to join both the tables.
 */

@Entity
@Table(
        name = "tenant_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_membership_user_tenant",
                columnNames = {"user_id", "tenant_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_membership_tenant",
                        columnList = "tenant_id"
                )
        }
)
public class TenantMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TenantRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TenantMembership() {
        // Required by JPA.
    }

    public TenantMembership(
            AppUser user,
            Tenant tenant,
            TenantRole role) {

        this.user = Objects.requireNonNull(user, "user must not be null");
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public TenantRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changeRole(TenantRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
    }
}
