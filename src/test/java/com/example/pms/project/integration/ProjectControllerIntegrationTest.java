
package com.example.pms.project.integration;

import com.example.pms.auth.controller.TenantSelectionController.TenantSelectionResponse;
import com.example.pms.auth.dto.LoginRequest;
import com.example.pms.auth.dto.LoginResponse;
import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.auth.dto.SelectTenantRequest;
import com.example.pms.auth.service.AuthService;
import com.example.pms.auth.service.LoginService;
import com.example.pms.project.controller.ProjectController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    private String createTenantScopedToken(String email) throws Exception {
        String password = "StrongPassword123";

        authService.register(new RegisterRequest(
                email, password, "Validation Test Organization"));

        LoginResponse login = loginService.login(
                new LoginRequest(email, password));

        Long tenantId = login.tenants().get(0).tenantId();

        String selectionRequest = objectMapper.writeValueAsString(
                new SelectTenantRequest(tenantId));

        String selectionResponse = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + login.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selectionRequest))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse selected = objectMapper.readValue(
                selectionResponse, TenantSelectionResponse.class);

        return selected.accessToken();
    }

    @Test
    void authenticatedTenantCanCreateAndListProjects() throws Exception {
        String email = "project-api@example.com";
        String password = "StrongPassword123";

        authService.register(new RegisterRequest(
                email,
                password,
                "Project API Organization"
        ));

        LoginResponse login = loginService.login(
                new LoginRequest(email, password)
        );

        // Select a tenant to obtain a tenant-scoped JWT.
        Long tenantId = login.tenants().get(0).tenantId();

        String selectionRequest = objectMapper.writeValueAsString(
                new SelectTenantRequest(tenantId)
        );

        String selectionResponse = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + login.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selectionRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse selected = objectMapper.readValue(
                selectionResponse,
                TenantSelectionResponse.class
        );

        String token = selected.accessToken();

        // Verify that the selected token contains the expected tenant.
        var selectedJwt = jwtDecoder.decode(token);

        assertThat(selectedJwt.getClaimAsString("tenant_id"))
                .isEqualTo(tenantId.toString());

        // Create a project using the tenant-scoped JWT.
        String createRequest = """
                {
                    "name": "Website Redesign"
                }
                """;

        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequest)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdProject = objectMapper.readTree(createResponse);

        assertThat(createdProject.get("id")).isNotNull();
        assertThat(createdProject.get("name").asText())
                .isEqualTo("Website Redesign");

        // List projects using the same tenant-scoped JWT.
        String listResponse = mockMvc.perform(
                        get("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode projects = objectMapper.readTree(listResponse);

        assertThat(projects.isArray()).isTrue();
        assertThat(projects.toString()).contains("Website Redesign");
    }

    @Test
    void tenantCannotSeeAnotherTenantsProjects() throws Exception {
        String password = "StrongPassword123";

        // Register two independent users, each with their own tenant.
        authService.register(new RegisterRequest(
                "tenant-a-projects@example.com",
                password,
                "Organization A"
        ));

        authService.register(new RegisterRequest(
                "tenant-b-projects@example.com",
                password,
                "Organization B"
        ));

        // Log in as Tenant A.
        LoginResponse loginA = loginService.login(
                new LoginRequest("tenant-a-projects@example.com", password)
        );

        // Select Tenant A and obtain its tenant-scoped JWT.
        Long tenantAId = loginA.tenants().get(0).tenantId();

        String selectionA = objectMapper.writeValueAsString(
                new SelectTenantRequest(tenantAId)
        );

        String selectedResponseA = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + loginA.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selectionA)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse selectedA = objectMapper.readValue(
                selectedResponseA,
                TenantSelectionResponse.class
        );

        String tokenA = selectedA.accessToken();

        // Create a project belonging to Tenant A.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + tokenA
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Tenant A Secret Project"}
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdProject = objectMapper.readTree(createResponse);
        String projectName = createdProject.get("name").asText();

        assertThat(projectName).isEqualTo("Tenant A Secret Project");

        // Log in as Tenant B and select Tenant B.
        LoginResponse loginB = loginService.login(
                new LoginRequest("tenant-b-projects@example.com", password)
        );

        Long tenantBId = loginB.tenants().get(0).tenantId();

        String selectionB = objectMapper.writeValueAsString(
                new SelectTenantRequest(tenantBId)
        );

        String selectedResponseB = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + loginB.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selectionB)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse selectedB = objectMapper.readValue(
                selectedResponseB,
                TenantSelectionResponse.class
        );

        // Tenant B must not see Tenant A's project.
        String listResponseB = mockMvc.perform(
                        get("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + selectedB.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode projectsB = objectMapper.readTree(listResponseB);

        assertThat(projectsB.isArray()).isTrue();
        assertThat(projectsB.toString())
                .doesNotContain("Tenant A Secret Project");
    }

    @Test
    void tenantCannotRetrieveAnotherTenantsProjectById() throws Exception {
        String password = "StrongPassword123";

        // Register two users with separate tenants.
        authService.register(new RegisterRequest(
                "project-owner@example.com",
                password,
                "Project Owner Organization"
        ));

        authService.register(new RegisterRequest(
                "other-tenant@example.com",
                password,
                "Other Tenant Organization"
        ));

        // Log in as the project owner.
        LoginResponse ownerLogin = loginService.login(
                new LoginRequest("project-owner@example.com", password)
        );

        // Select the owner's tenant.
        Long ownerTenantId = ownerLogin.tenants().get(0).tenantId();

        String ownerSelectionRequest = objectMapper.writeValueAsString(
                new SelectTenantRequest(ownerTenantId)
        );

        String ownerSelectionResponse = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + ownerLogin.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ownerSelectionRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse ownerSelected = objectMapper.readValue(
                ownerSelectionResponse,
                TenantSelectionResponse.class
        );

        String ownerToken = ownerSelected.accessToken();

        // Create a project belonging to the owner's tenant.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + ownerToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Private Tenant A Project"}
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdProject = objectMapper.readTree(createResponse);
        Long projectId = createdProject.get("id").asLong();

        assertThat(projectId).isPositive();

        // Log in as the other tenant.
        LoginResponse otherLogin = loginService.login(
                new LoginRequest("other-tenant@example.com", password)
        );

        Long otherTenantId = otherLogin.tenants().get(0).tenantId();

        String otherSelectionRequest = objectMapper.writeValueAsString(
                new SelectTenantRequest(otherTenantId)
        );

        String otherSelectionResponse = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + otherLogin.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(otherSelectionRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse otherSelected = objectMapper.readValue(
                otherSelectionResponse,
                TenantSelectionResponse.class
        );

        // Tenant B must not retrieve Tenant A's project by ID.
        mockMvc.perform(
                        get("/api/projects/" + projectId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + otherSelected.accessToken()
                                )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void createProjectRejectsBlankName() throws Exception {
        String token = createTenantScopedToken("blank-name@example.com");

        mockMvc.perform(
                        post("/api/projects")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {"name":"   "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("Project name is required"));

    }

    @Test
    void createProjectRejectsMissingName() throws Exception {
        String token = createTenantScopedToken("missing-name@example.com");

        mockMvc.perform(
                        post("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("Project name is required"));
    }

    @Test
    void createProjectRejectsNameLongerThan120Characters() throws Exception {
        String token = createTenantScopedToken("long-name@example.com");

        String longName = "A".repeat(121);
        String requestBody = objectMapper.writeValueAsString(
                new ProjectController.CreateProjectRequest(longName));

        mockMvc.perform(
                        post("/api/projects")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]")
                        .value("Project name must not exceed 120 characters"));
    }

    @Test
    void tenantCanUpdateOwnProject() throws Exception {
        String token = createTenantScopedToken("update-project@example.com");

        // Create a project.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Original Project Name"}
                                    """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdProject = objectMapper.readTree(createResponse);
        long projectId = createdProject.get("id").asLong();

        // Update the project.
        String updateResponse = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Updated Project Name"}
                                    """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updatedProject = objectMapper.readTree(updateResponse);

        assertThat(updatedProject.get("id").asLong()).isEqualTo(projectId);
        assertThat(updatedProject.get("name").asText())
                .isEqualTo("Updated Project Name");

        // Confirm the updated name persists when the project is retrieved.
        mockMvc.perform(
                        get("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Project Name"));
    }

    @Test
    void updateProjectRejectsBlankName() throws Exception {
        String token = createTenantScopedToken("update-blank-name@example.com");

        // Create a project first.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Project Before Update"}
                                    """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long projectId = objectMapper.readTree(createResponse)
                .get("id").asLong();

        // Attempt to update it with a blank name.
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"   "}
                                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]")
                        .value("Project name is required"));
    }

    @Test
    void tenantCannotUpdateAnotherTenantsProject() throws Exception {
        String password = "StrongPassword123";

        // Obtain Tenant A's scoped token.
        String ownerToken = createTenantScopedToken("update-owner@example.com");

        // Create a project belonging to Tenant A.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Original Private Project"}
                                    """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long projectId = objectMapper.readTree(createResponse)
                .get("id").asLong();

        // Obtain Tenant B's scoped token.
        String otherToken = createTenantScopedToken("update-other@example.com");

        // Tenant B attempts to update Tenant A's project.
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Unauthorized Modification"}
                                    """))
                .andExpect(status().isNotFound());

        // Confirm Tenant A's project remains unchanged.
        mockMvc.perform(
                        get("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Original Private Project"));
    }

    @Test
    void tenantCanDeleteOwnProject() throws Exception {
        String token = createTenantScopedToken("delete-project@example.com");

        // Create a project.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Project To Delete"}
                                    """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long projectId = objectMapper.readTree(createResponse)
                .get("id").asLong();

        // Delete the project.
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // Confirm the project is no longer visible.
        mockMvc.perform(
                        get("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantCannotDeleteAnotherTenantsProject() throws Exception {
        String ownerToken = createTenantScopedToken("delete-owner@example.com");
        String otherToken = createTenantScopedToken("delete-other@example.com");

        // Tenant A creates a project.
        String createResponse = mockMvc.perform(
                        post("/api/projects")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"name":"Protected Project"}
                                    """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long projectId = objectMapper.readTree(createResponse)
                .get("id").asLong();

        // Tenant B attempts to delete Tenant A's project.
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Tenant A's project must still exist.
        mockMvc.perform(
                        get("/api/projects/" + projectId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Protected Project"));
    }



}
