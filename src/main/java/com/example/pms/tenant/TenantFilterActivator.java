package com.example.pms.tenant;

import com.example.pms.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TenantFilterActivator {

    public void activate(EntityManager entityManager) {
        Objects.requireNonNull(
                entityManager,
                "entityManager must not be null"
        );
        String tenantId = TenantContext.getTenantId();

        Session session = entityManager.unwrap(Session.class);

        session.enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId);
    }


}
