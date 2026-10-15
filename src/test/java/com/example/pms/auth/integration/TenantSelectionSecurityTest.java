package com.example.pms.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantSelectionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedUserCannotSelectTenant() throws Exception {
        mockMvc.perform(post("/api/auth/select-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": 1}
                                """))
                .andExpect(status().isUnauthorized());
    }
}