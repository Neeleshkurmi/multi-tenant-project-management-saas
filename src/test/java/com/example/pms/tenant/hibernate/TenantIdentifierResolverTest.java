package com.example.pms.tenant.hibernate;

import com.example.pms.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TenantIdentifierResolverTest {

    private final TenantIdentifierResolver resolver =
            new TenantIdentifierResolver();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldResolveCurrentTenant() {
        TenantContext.setTenantId("tenant-a");

        assertThat(resolver.resolveCurrentTenantIdentifier())
                .isEqualTo("tenant-a");
    }

    @Test
    void shouldFailWhenTenantIsMissing() {
        assertThatIllegalStateException()
                .isThrownBy(resolver::resolveCurrentTenantIdentifier)
                .withMessageContaining("No tenant");
    }

    @Test
    void shouldValidateExistingCurrentSessions() {
        assertThat(resolver.validateExistingCurrentSessions())
                .isTrue();
    }
}