package com.example.pms.project.controller;

import com.example.pms.project.entity.Project;
import com.example.pms.project.entity.ProjectStatus;
import com.example.pms.project.services.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        Project project = projectService.createProject(
                extractUserId(jwt),
                request.name()
        );

        return toResponse(project);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId
    ) {
        projectService.archiveProject(
                extractUserId(jwt),
                projectId
        );
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        Project project = projectService.updateProject(
                extractUserId(jwt),
                projectId,
                request.name()
        );

        return toResponse(project);
    }

    @GetMapping
    public List<ProjectResponse> getProjects(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return projectService.getProjects(extractUserId(jwt))
                .stream()
                .map(ProjectController::toResponse)
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId
    ) {
        Project project = projectService.getProject(
                extractUserId(jwt),
                projectId
        );

        return toResponse(project);
    }

    private Long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Invalid authenticated user");
        }
    }

    private static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getStatus()
        );
    }

    public record CreateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 150, message = "Project name must not exceed 150 characters")
            String name
    ) {}

    public record UpdateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 150, message = "Project name must not exceed 150 characters")
            String name
    ) {}

    public record ProjectResponse(
            Long id,
            String name,
            ProjectStatus status
    ) {}
}