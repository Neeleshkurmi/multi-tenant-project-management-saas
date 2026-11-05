package com.example.pms.project.repository;

import com.example.pms.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByTenantIdAndNameIgnoreCase(
            String tenantId,
            String name
    );

    List<Project> findAllByStatus(
            com.example.pms.project.entity.ProjectStatus status
    );
}