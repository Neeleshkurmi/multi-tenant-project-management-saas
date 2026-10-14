package com.example.pms.tenant.context;

import java.util.Objects;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }

        String normalizedTenantId = tenantId.trim();

        String existingTenantId = CURRENT_TENANT.get();

        if (existingTenantId != null && !existingTenantId.equals(normalizedTenantId)) {
            throw new IllegalStateException(
                    "Tenant context is already initialized for tenant: " + existingTenantId
            );
        }

        CURRENT_TENANT.set(normalizedTenantId);
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();

        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant is associated with the current execution context"
            );
        }

        return tenantId;
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}