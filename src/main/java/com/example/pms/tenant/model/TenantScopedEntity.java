package com.example.pms.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import com.example.pms.tenant.context.TenantContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/* this class manage the scope of tenant context, while switching between tenant, prevents data leaks.
    by applying validation checks.
 */

@MappedSuperclass
@Getter
@Setter
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = String.class),
        applyToLoadByKey = true
)
public abstract class TenantScopedEntity {

    @Column(
            name = "tenant_id",
            nullable = false,
            updatable = false,
            length = 64
    )
    private String tenantId;

    protected TenantScopedEntity() {
    }

    protected TenantScopedEntity(String tenantId) {
        assignTenant(tenantId);
    }

    public void assignTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantId must not be null or blank"
            );
        }

        if (this.tenantId != null && !this.tenantId.equals(tenantId)) {
            throw new IllegalStateException(
                    "Entity is already assigned to tenant: " + this.tenantId
            );
        }

        this.tenantId = tenantId;
    }

    @PrePersist
    @PreUpdate
    private void validateTenantOwnership() {
        String currentTenantId = TenantContext.getTenantId();

        if (tenantId == null || !tenantId.equals(currentTenantId)) {
            throw new IllegalStateException(
                    "Entity tenant does not match the current tenant context");
        }
    }
}