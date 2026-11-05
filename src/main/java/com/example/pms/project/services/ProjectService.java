package com.example.pms.project.services;

import com.example.pms.project.entity.Project;
import com.example.pms.project.entity.ProjectStatus;
import com.example.pms.project.exception.ProjectAlreadyExistsException;
import com.example.pms.project.exception.ProjectNotFoundException;
import com.example.pms.project.repository.ProjectRepository;
import com.example.pms.tenant.TenantFilterActivator;
import com.example.pms.tenant.context.TenantContext;
import com.example.pms.tenant.entity.TenantRole;
import com.example.pms.tenant.services.TenantAuthorizationService;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TenantFilterActivator tenantFilterActivator;
    private final TenantAuthorizationService authorizationService;
    private final EntityManager entityManager;

    @Transactional
    public Project createProject(Long userId, String name) {
        String tenantId = getSelectedTenantId();

        authorizationService.requireRole(
                userId,
                Long.valueOf(tenantId),
                TenantRole.OWNER,
                TenantRole.ADMIN
        );

        tenantFilterActivator.activate(entityManager);

        String normalizedName = validateName(name);

        if (projectRepository.existsByTenantIdAndNameIgnoreCase(
                tenantId,
                normalizedName
        )) {
            throw new ProjectAlreadyExistsException();
        }

        Project project = new Project(tenantId, normalizedName);

        try {
            return projectRepository.save(project);
        } catch (DataIntegrityViolationException exception) {
            // The pre-check gives a friendly fast path; the database constraint
            // remains authoritative when two creates race past that check.
            throw new ProjectAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public List<Project> getProjects(Long userId) {
        authorizationService.requireMembership(
                userId,
                Long.valueOf(getSelectedTenantId())
        );

        tenantFilterActivator.activate(entityManager);

        return projectRepository.findAllByStatus(ProjectStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Project getProject(Long userId, Long projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");

        authorizationService.requireMembership(
                userId,
                Long.valueOf(getSelectedTenantId())
        );

        tenantFilterActivator.activate(entityManager);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectNotFoundException(projectId);
        }

        return project;
    }

    @Transactional
    public Project updateProject(
            Long userId,
            Long projectId,
            String name
    ) {
        Objects.requireNonNull(projectId, "projectId must not be null");

        String tenantId = getSelectedTenantId();

        authorizationService.requireRole(
                userId,
                Long.valueOf(tenantId),
                TenantRole.OWNER,
                TenantRole.ADMIN
        );

        tenantFilterActivator.activate(entityManager);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        String normalizedName = validateName(name);

        if (!project.getName().equalsIgnoreCase(normalizedName)
                && projectRepository.existsByTenantIdAndNameIgnoreCase(
                tenantId,
                normalizedName
        )) {
            throw new ProjectAlreadyExistsException();
        }

        project.rename(normalizedName);

        return project;
    }

    @Transactional
    public void archiveProject(Long userId, Long projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");

        authorizationService.requireRole(
                userId,
                Long.valueOf(getSelectedTenantId()),
                TenantRole.OWNER,
                TenantRole.ADMIN
        );

        tenantFilterActivator.activate(entityManager);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        project.archive();
    }

    private String getSelectedTenantId() {
        return TenantContext.getTenantId();
    }

    private String validateName(String name) {
        Objects.requireNonNull(name, "Project name must not be null");

        String normalized = name.trim();

        if (normalized.isEmpty() || normalized.length() > 150) {
            throw new IllegalArgumentException(
                    "Project name must contain between 1 and 150 characters"
            );
        }

        return normalized;
    }
}
