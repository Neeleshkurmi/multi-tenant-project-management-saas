
package com.example.pms.project.controller;

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
    public Project createProject(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.createProject(request.name());
    }

    @GetMapping
    public List<Project> getProjects() {
        return projectService.getProjects();
    }

    public record CreateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 120, message = "Project name must not exceed 120 characters")
            String name
    ) {
    }
}
