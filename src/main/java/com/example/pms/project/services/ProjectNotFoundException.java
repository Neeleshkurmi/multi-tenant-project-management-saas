package com.example.pms.project.services;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long projectId) {
        super("Project not found: " + projectId);
    }
}
