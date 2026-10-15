package com.example.pms.project.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/* ProjectTenantOwnershipTest -> this class tests the cross tenant features.
 */

class ProjectTenantOwnershipTest {

    @Test
    void projectCannotBeReassignedToAnotherTenant() {
        Project project = new Project("tenant-a", "Project Alpha");

        assertThat(project.getTenantId()).isEqualTo("tenant-a");

        assertThatThrownBy(() -> project.assignTenant("tenant-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already assigned to tenant");

        assertThat(project.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void projectRejectsBlankTenantId() {
        assertThatThrownBy(() -> new Project(" ", "Project Alpha"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}