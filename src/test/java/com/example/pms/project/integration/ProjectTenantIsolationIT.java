package com.example.pms.project.integration;

import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.auth.service.AuthService;
import com.example.pms.project.entity.Project;
import com.example.pms.project.exception.ProjectAlreadyExistsException;
import com.example.pms.project.exception.ProjectNotFoundException;
import com.example.pms.project.repository.ProjectRepository;
import com.example.pms.project.services.ProjectService;
import com.example.pms.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class ProjectTenantIsolationIT {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void tenantCanReadItsOwnProjects() {
        Registration tenant = registerTenant(
                "alpha-owner@example.com",
                "Alpha Organization"
        );

        TenantContext.setTenantId(tenant.tenantId().toString());

        Project project = projectService.createProject(
                tenant.userId(),
                "Alpha Project"
        );

        List<Project> projects =
                projectService.getProjects(tenant.userId());

        assertThat(projects)
                .extracting(Project::getId)
                .contains(project.getId());

        assertThat(projects)
                .extracting(Project::getTenantId)
                .containsOnly(tenant.tenantId().toString());
    }

    @Test
    void tenantCannotSeeAnotherTenantsProjects() {
        Registration tenantA = registerTenant(
                "tenant-a@example.com",
                "Organization A"
        );

        Registration tenantB = registerTenant(
                "tenant-b@example.com",
                "Organization B"
        );

        TenantContext.setTenantId(tenantA.tenantId().toString());

        projectService.createProject(
                tenantA.userId(),
                "Private Alpha Project"
        );

        TenantContext.clear();
        TenantContext.setTenantId(tenantB.tenantId().toString());

        List<Project> projects =
                projectService.getProjects(tenantB.userId());

        assertThat(projects).isEmpty();
    }

    @Test
    void tenantCannotLoadAnotherTenantsProjectById() {
        Registration tenantA = registerTenant(
                "project-owner-a@example.com",
                "Project Owner Organization"
        );

        Registration tenantB = registerTenant(
                "project-owner-b@example.com",
                "Other Organization"
        );

        TenantContext.setTenantId(tenantA.tenantId().toString());

        Project project = projectService.createProject(
                tenantA.userId(),
                "Alpha Secret"
        );

        TenantContext.clear();
        TenantContext.setTenantId(tenantB.tenantId().toString());

        assertThatThrownBy(() ->
                projectService.getProject(
                        tenantB.userId(),
                        project.getId()
                )
        ).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void concurrentTenantsRemainIsolated() throws Exception {
        Registration tenantA = registerTenant(
                "concurrent-a@example.com",
                "Concurrent Organization A"
        );

        Registration tenantB = registerTenant(
                "concurrent-b@example.com",
                "Concurrent Organization B"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<List<Project>> futureA =
                    CompletableFuture.supplyAsync(
                            () -> createAndReadForTenant(
                                    tenantA,
                                    "Project A"
                            ),
                            executor
                    );

            CompletableFuture<List<Project>> futureB =
                    CompletableFuture.supplyAsync(
                            () -> createAndReadForTenant(
                                    tenantB,
                                    "Project B"
                            ),
                            executor
                    );

            List<Project> projectsA =
                    futureA.get(30, TimeUnit.SECONDS);

            List<Project> projectsB =
                    futureB.get(30, TimeUnit.SECONDS);

            assertThat(projectsA)
                    .extracting(Project::getTenantId)
                    .containsOnly(tenantA.tenantId().toString());

            assertThat(projectsB)
                    .extracting(Project::getTenantId)
                    .containsOnly(tenantB.tenantId().toString());

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
        Registration tenant = registerTenant(
                "missing-context@example.com",
                "Missing Context Organization"
        );

        TenantContext.clear();

        assertThatThrownBy(() ->
                projectService.getProjects(tenant.userId())
        ).isInstanceOf(RuntimeException.class);

        TenantContext.clear();
    }

    @Test
    void cannotPersistProjectOwnedByAnotherTenant() {
        Project project = new Project(
                "1001",
                "Tenant A Project"
        );

        TenantContext.setTenantId("1002");

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

    @Test
    void createDuplicateProjectWithSameNameAsArchiveProject() throws Exception {
        Registration registration = registerTenant("create-tenant@example.com", "cr-t");

        TenantContext.setTenantId(String.valueOf(registration.tenantId));

        Project project = projectService.createProject(registration.userId, "Project");

        projectService.archiveProject(registration.userId, project.getId());

        assertThatThrownBy( () ->
                projectService.createProject(registration.userId, "Project"))
                .isInstanceOf(ProjectAlreadyExistsException.class);
        TenantContext.clear();
    }

    @Test
    void archivedProjectNameRemainsReservedIgnoringCase() {
        Registration registration = registerTenant("create-case-sensitive@example.com", "cr-t");

        TenantContext.setTenantId(String.valueOf(registration.tenantId));

        Project project = projectService.createProject(registration.userId, "PROJECT");
         projectService.archiveProject(registration.userId, project.getId());

        assertThatThrownBy(() ->
                projectService.createProject(registration.userId, "ProjecT"))
                .isInstanceOf(ProjectAlreadyExistsException.class);

        Project archivedProject = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(archivedProject.getStatus())
                .isEqualTo(com.example.pms.project.entity.ProjectStatus.ARCHIVED);
        assertThat(projectService.getProjects(registration.userId)).isEmpty();
    }

    @Test
    void activeProjectNamesAreUniqueIgnoringCase() {
        Registration registration = registerTenant("create-active-case@example.com", "cr-t");
        TenantContext.setTenantId(registration.tenantId().toString());

        projectService.createProject(registration.userId(), "PROJECT");

        assertThatThrownBy(() -> projectService.createProject(registration.userId(), "ProjecT"))
                .isInstanceOf(ProjectAlreadyExistsException.class);
    }

    @Test
    void concurrentCreatesWithSameTenantAndNameAllowOnlyOneProject() throws Exception {
        Registration registration = registerTenant(
                "concurrent-duplicate@example.com",
                "Concurrent Duplicate Organization"
        );
        int contenders = 2;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejectedAsDuplicate = new AtomicInteger();

        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (int index = 0; index < contenders; index++) {
                attempts.add(executor.submit(() -> {
                        TenantContext.setTenantId(registration.tenantId().toString());
                        ready.countDown();
                        try {
                            if (!start.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting to start duplicate create");
                            }
                            projectService.createProject(registration.userId(), "Shared Project");
                            created.incrementAndGet();
                        } catch (ProjectAlreadyExistsException exception) {
                            rejectedAsDuplicate.incrementAndGet();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Duplicate create was interrupted", exception);
                        } finally {
                            TenantContext.clear();
                        }
                    }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }

            TenantContext.setTenantId(registration.tenantId().toString());
            assertThat(created.get()).isEqualTo(1);
            assertThat(rejectedAsDuplicate.get()).isEqualTo(1);
            assertThat(projectService.getProjects(registration.userId()))
                    .extracting(Project::getName)
                    .containsExactly("Shared Project");
        } finally {
            start.countDown();
            TenantContext.clear();
            executor.shutdownNow();
        }
    }


    private List<Project> createAndReadForTenant(
            Registration tenant,
            String projectName
    ) {
        try {
            TenantContext.setTenantId(
                    tenant.tenantId().toString()
            );

            projectService.createProject(
                    tenant.userId(),
                    projectName
            );

            return projectService.getProjects(
                    tenant.userId()
            );

        } finally {
            TenantContext.clear();
        }
    }

    private Registration registerTenant(
            String email,
            String organizationName
    ) {
        var response = authService.register(
                new RegisterRequest(
                        email,
                        "StrongPassword123",
                        organizationName
                )
        );

        return new Registration(
                response.userId(),
                response.tenantId()
        );
    }

    private record Registration(
            Long userId,
            Long tenantId
    ) {}
}
