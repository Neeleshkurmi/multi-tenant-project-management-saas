package com.example.pms.tenant.hibernate;

import com.example.pms.tenant.TenantFilterActivator;
import com.example.pms.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Aspect
@Component
public class TenantFilterAspect {

    private final TenantFilterActivator tenantFilterActivator;
    private final EntityManager entityManager;

    public TenantFilterAspect(
            TenantFilterActivator tenantFilterActivator,
            EntityManager entityManager) {
        this.tenantFilterActivator = Objects.requireNonNull(
                tenantFilterActivator);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void activateTenantFilter() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Tenant-scoped database access requires an active transaction");
        }

        TenantContext.getTenantId();
        tenantFilterActivator.activate(entityManager);
    }
}
