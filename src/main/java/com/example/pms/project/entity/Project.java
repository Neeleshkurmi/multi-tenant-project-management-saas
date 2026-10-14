package com.example.pms.project.entity;

import com.example.pms.tenant.model.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(
                        name = "idx_projects_tenant_id",
                        columnList = "tenant_id"
                )
        }
)
@Filter(
        name = "tenantFilter",
        condition = "tenant_id = :tenantId"
)
public class Project extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    protected  Project() {}

    public Project(String tenantId, String name) {
        super(tenantId);
        this.name = requireValidName(name);
    }

    public void rename(String name) {
        this.name = requireValidName(name);
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "Project name must not be null");

        String normalized = name.trim();

        if(normalized.isEmpty() || normalized.length() > 150) {
            throw new IllegalArgumentException(
                    "Project name must contains between 1 and 150 characters"
            );
        }
        return normalized;
    }
}
