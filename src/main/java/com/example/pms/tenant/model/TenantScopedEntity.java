package com.example.pms.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

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
}