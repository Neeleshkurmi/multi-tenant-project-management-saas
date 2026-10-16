
package com.example.pms.auth.integration;

import com.example.pms.auth.dto.LoginRequest;
import com.example.pms.auth.dto.LoginResponse;
import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.auth.dto.SelectTenantRequest;
import com.example.pms.auth.controller.TenantSelectionController.TenantSelectionResponse;
import com.example.pms.auth.service.AuthService;
import com.example.pms.auth.service.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class TenantSelectionIntegrationTest {

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
    void userCanSelectOwnTenantAndReceiveTenantScopedJwt() throws Exception {
        String email = "tenant-selection@example.com";
        String password = "StrongPassword123";

        var registration = authService.register(new RegisterRequest(
                email, password, "Tenant Selection Organization"));

        LoginResponse login = loginService.login(
                new LoginRequest(email, password));

        String requestBody = objectMapper.writeValueAsString(
                new SelectTenantRequest(registration.tenantId()));

        String responseBody = mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .with(SecurityMockMvcRequestPostProcessors
                                        .authentication(
                                                new JwtAuthenticationToken(
                                                        Jwt
                                                            .withTokenValue(login.accessToken())
                                                            .header("alg", "HS256")
                                                            .subject(login.userId().toString())
                                                            .claim("email", email)
                                                            .issuedAt(java.time.Instant.now())
                                                            .expiresAt(java.time.Instant.now().plusSeconds(3600))
                                                            .build(),
                                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                                        )))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TenantSelectionResponse selected = objectMapper.readValue(
                responseBody, TenantSelectionResponse.class);

        assertThat(selected.accessToken()).isNotBlank();
        assertThat(selected.tokenType()).isEqualTo("Bearer");
        assertThat(selected.tenantId()).isEqualTo(registration.tenantId());
        assertThat(selected.role()).isEqualTo("OWNER");

        var selectedJwt = jwtDecoder.decode(selected.accessToken());

        assertThat(selectedJwt.getSubject())
                .isEqualTo(login.userId().toString());

        assertThat(selectedJwt.getClaimAsString("tenant_id"))
                .isEqualTo(registration.tenantId().toString());

        assertThat(selectedJwt.getClaimAsString("role"))
                .isEqualTo("OWNER");
    }


    @Test
    void userCannotSelectAnotherUsersTenant() throws Exception {
        var userA = authService.register(new RegisterRequest(
                "tenant-a@example.com",
                "StrongPassword123",
                "Organization A"));

        var userB = authService.register(new RegisterRequest(
                "tenant-b@example.com",
                "StrongPassword123",
                "Organization B"));

        LoginResponse loginA = loginService.login(
                new LoginRequest(
                        "tenant-a@example.com",
                        "StrongPassword123"));

        String requestBody = objectMapper.writeValueAsString(
                new SelectTenantRequest(userB.tenantId()));

        mockMvc.perform(
                        post("/api/auth/select-tenant")
                                .with(SecurityMockMvcRequestPostProcessors
                                        .authentication(
                                                new JwtAuthenticationToken(Jwt
                                                            .withTokenValue(loginA.accessToken())
                                                            .header("alg", "HS256")
                                                            .subject(loginA.userId().toString())
                                                            .claim("email", "tenant-a@example.com")
                                                            .issuedAt(java.time.Instant.now())
                                                            .expiresAt(java.time.Instant.now().plusSeconds(3600))
                                                            .build(),
                                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                                        )))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isForbidden());
    }

}
