
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
}
