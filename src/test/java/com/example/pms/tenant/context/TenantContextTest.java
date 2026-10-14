package com.example.pms.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSetAndRetrieveTenantId() {
        TenantContext.setTenantId("tenant-a");

        assertThat(TenantContext.hasTenant()).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void shouldNormalizeTenantId() {
        TenantContext.setTenantId("  tenant-a  ");

        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void shouldRejectNullTenantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> TenantContext.setTenantId(null));
    }

    @Test
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TenantContext.setTenantId("   "));
    }

    @Test
    void shouldRejectChangingTenantWithinSameExecutionContext() {
        TenantContext.setTenantId("tenant-a");

        assertThatIllegalStateException()
                .isThrownBy(() -> TenantContext.setTenantId("tenant-b"))
                .withMessageContaining("tenant-a");
    }

    @Test
    void shouldRejectReadingMissingTenant() {
        assertThatIllegalStateException()
                .isThrownBy(TenantContext::getTenantId)
                .withMessageContaining("No tenant");
    }

    @Test
    void shouldClearTenantContext() {
        TenantContext.setTenantId("tenant-a");

        TenantContext.clear();

        assertThat(TenantContext.hasTenant()).isFalse();
    }
}