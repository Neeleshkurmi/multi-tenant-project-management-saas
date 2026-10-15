package com.example.pms.project.integration;


import com.example.pms.project.entity.Project;
import com.example.pms.project.services.ProjectNotFoundException;
import com.example.pms.project.services.ProjectService;
import com.example.pms.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.example.pms.project.entity.Project;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class ProjectTenantIsolationIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("pms_test")
                    .withUsername("pms_test")
                    .withPassword("pms_test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name",
                MYSQL::getDriverClassName);
    }

    @Autowired
    private ProjectService projectService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;



    @Test
    void tenantCanReadItsOwnProjects() {
        String tenantA = tenantId();

        TenantContext.setTenantId(tenantA);
        Project project = projectService.createProject("Alpha Project");

        List<Project> projects = projectService.getProjects();

        assertThat(projects)
                .extracting(Project::getId)
                .contains(project.getId());

        assertThat(projects)
                .extracting(Project::getTenantId)
                .containsOnly(tenantA);
    }

    @Test
    void tenantCannotSeeAnotherTenantsProjects() {
        String tenantA = tenantId();
        String tenantB = tenantId();

        TenantContext.setTenantId(tenantA);
        projectService.createProject("Private Alpha Project");

        TenantContext.clear();
        TenantContext.setTenantId(tenantB);

        assertThat(projectService.getProjects()).isEmpty();
    }

    @Test
    void tenantCannotLoadAnotherTenantsProjectById() {
        String tenantA = tenantId();
        String tenantB = tenantId();

        TenantContext.setTenantId(tenantA);
        Project project = projectService.createProject("Alpha Secret");

        TenantContext.clear();
        TenantContext.setTenantId(tenantB);

        assertThatThrownBy(() -> projectService.getProject(project.getId()))
                .isInstanceOf(ProjectNotFoundException.class);
    }


    @Test
    void concurrentTenantsRemainIsolated() throws Exception {
        String tenantA = tenantId();
        String tenantB = tenantId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<List<Project>> futureA =
                    CompletableFuture.supplyAsync(
                            () -> createAndReadForTenant(tenantA, "Project A"),
                            executor
                    );

            CompletableFuture<List<Project>> futureB =
                    CompletableFuture.supplyAsync(
                            () -> createAndReadForTenant(tenantB, "Project B"),
                            executor
                    );

            List<Project> projectsA = futureA.get(30, TimeUnit.SECONDS);
            List<Project> projectsB = futureB.get(30, TimeUnit.SECONDS);

            assertThat(projectsA)
                    .extracting(Project::getTenantId)
                    .containsOnly(tenantA);

            assertThat(projectsB)
                    .extracting(Project::getTenantId)
                    .containsOnly(tenantB);

            assertThat(projectsA)
                    .extracting(Project::getName)
                    .containsExactly("Project A");

            assertThat(projectsB)
                    .extracting(Project::getName)
                    .containsExactly("Project B");
        } finally {
            executor.shutdownNow();
        }
    }



    @Test
    void missingTenantFailsClosed() {
        TenantContext.clear();

        assertThatThrownBy(() -> projectService.getProjects())
                .isInstanceOf(RuntimeException.class);

        TenantContext.clear();
    }


    @Test
    void cannotPersistProjectOwnedByAnotherTenant() {
        Project project = new Project("tenant-a", "Tenant A project");

        TenantContext.setTenantId("tenant-b");

        try {
            assertThatThrownBy(() ->
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status ->
                                    entityManager.persist(project)
                            )
            ).isInstanceOf(RuntimeException.class);
        } finally {
            TenantContext.clear();
        }
    }



    private List<Project> createAndReadForTenant(
            String tenantId,
            String projectName
    ) {
        try {
            TenantContext.setTenantId(tenantId);
            projectService.createProject(projectName);
            return projectService.getProjects();
        } finally {
            TenantContext.clear();
        }
    }

    private String tenantId() {
        return "tenant-" + UUID.randomUUID();
    }
}
