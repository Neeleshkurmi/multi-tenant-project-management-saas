
package com.example.pms.project.integration;

import com.example.pms.auth.controller.TenantSelectionController.TenantSelectionResponse;
import com.example.pms.auth.dto.LoginRequest;
import com.example.pms.auth.dto.LoginResponse;
import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.auth.dto.SelectTenantRequest;
import com.example.pms.auth.service.AuthService;
import com.example.pms.auth.service.LoginService;
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

}
