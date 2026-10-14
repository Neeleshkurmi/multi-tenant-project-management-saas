package com.example.pms.project.services;

import com.example.pms.project.entity.Project;
import com.example.pms.project.repository.ProjectRepository;
import com.example.pms.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional
    public Project createProject(String name) {

        String tenantId = TenantContext.getTenantId();

        Project project = new Project(tenantId, name);

        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjects() {

        return projectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public void getProject(Long projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");

        projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
