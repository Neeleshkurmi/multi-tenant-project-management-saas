package com.example.pms.project.exception;

public class ProjectAlreadyExistsException extends RuntimeException {

    public ProjectAlreadyExistsException() {
        super("A project with this name already exists");
    }
}