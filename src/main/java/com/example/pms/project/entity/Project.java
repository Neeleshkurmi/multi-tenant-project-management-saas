package com.example.pms.project.entity;

import com.example.pms.tenant.model.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Filter;

import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "projects",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_tenant_name",
                        columnNames = {"tenant_id", "name"}
                )
        },
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    protected Project() {
    }

    public Project(String tenantId, String name) {
        super(tenantId);
        this.name = normalizeName(name);
        this.status = ProjectStatus.ACTIVE;
    }

    public void rename(String name) {
        if (status == ProjectStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "Archived projects cannot be renamed"
            );
        }

        this.name = normalizeName(name);
    }

    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
    }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "Project name must not be null");

        String normalized = name.trim();

        if (normalized.isEmpty() || normalized.length() > 150) {
            throw new IllegalArgumentException(
                    "Project name must contain between 1 and 150 characters"
            );
        }

        return normalized;
    }

    public String getNormalizedName() {
        return name.toLowerCase(Locale.ROOT);
    }
}