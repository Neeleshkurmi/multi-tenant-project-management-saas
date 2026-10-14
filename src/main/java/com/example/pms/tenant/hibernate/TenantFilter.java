package com.example.pms.tenant.hibernate;


import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(
                name = "tenantId",
                type = String.class
        )
)
public final class TenantFilter {

    public static final String NAME = "tenantFilter";
    public static final String TENANT_ID_PARAMETER = "tenantId";

    private TenantFilter() {}

}
