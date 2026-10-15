package com.example.pms.tenant;

import com.example.pms.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.Objects;

/* this class activates a filter for tenant when needed.
   the filter adds a WHERE clause for every query to prevent data leaks and achieve true isolation.
   - uses the functionality of TenantFilter class, TenantFilter provides the filtering logic for a hibernate session.
 */

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
