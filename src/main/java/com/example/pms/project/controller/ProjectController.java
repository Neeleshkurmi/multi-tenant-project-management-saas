
package com.example.pms.project.controller;

import com.example.pms.project.dto.ProjectResponse;
import com.example.pms.project.entity.Project;
import com.example.pms.project.services.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @Valid @RequestBody CreateProjectRequest request) {

        Project project = projectService.createProject(request.name());
        return toResponse(project);
    }

    @GetMapping
    public List<ProjectResponse> getProjects() {
        return projectService.getProjects()
                .stream()
                .map(ProjectController::toResponse)
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable Long projectId) {
        Project project = projectService.getProject(projectId);
        return toResponse(project);
    }

    private static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName());
    }

    public record CreateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 120, message = "Project name must not exceed 120 characters")
            String name
    ) {
    }
}