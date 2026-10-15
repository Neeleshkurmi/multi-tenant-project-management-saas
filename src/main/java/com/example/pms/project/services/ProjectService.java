package com.example.pms.project.services;

import com.example.pms.project.entity.Project;
import com.example.pms.project.repository.ProjectRepository;
import com.example.pms.tenant.TenantFilterActivator;
import com.example.pms.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/* ProjectService -> this class provides the business logic for managing tenant projects.
                     * applies and clears a tenant filter for each transaction.
 */

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TenantFilterActivator tenantFilterActivator;
    private final EntityManager entityManager;

    @Transactional
    public Project createProject(String name) {
        tenantFilterActivator.activate(entityManager);

        String tenantId = TenantContext.getTenantId();

        Project project = new Project(tenantId, name);

        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjects() {
        tenantFilterActivator.activate(entityManager);

        return projectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public void getProject(Long projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");

        tenantFilterActivator.activate(entityManager);

        projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
