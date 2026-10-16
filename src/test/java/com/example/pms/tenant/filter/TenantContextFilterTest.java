
package com.example.pms.tenant.filter;

import com.example.pms.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantContextIsClearedAfterRequest() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("42")
                .claim("tenant_id", "7")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        java.util.List.of(
                                new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_USER")));

        assertThat(authentication.isAuthenticated()).isTrue();

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean tenantWasPresentDuringRequest = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("7");
            tenantWasPresentDuringRequest.set(true);
        };

        filter.doFilter(request, response, chain);

        assertThat(tenantWasPresentDuringRequest).isTrue();
        assertThat(TenantContext.hasTenant()).isFalse();
    }


    @Test
    void tenantContextIsClearedWhenRequestProcessingThrows() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("42")
                .claim("tenant_id", "7")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        var authentication = new JwtAuthenticationToken(
                jwt,
                java.util.List.of(
                        new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_USER")));

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("7");
            throw new jakarta.servlet.ServletException("Simulated request failure");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> filter.doFilter(request, response, chain))
                .isInstanceOf(jakarta.servlet.ServletException.class)
                .hasMessage("Simulated request failure");

        assertThat(TenantContext.hasTenant()).isFalse();
    }


    @Test
    void requestWithoutTenantClaimDoesNotSetTenantContext() throws Exception {
        Jwt jwt = Jwt.withTokenValue("user-only-token")
                .header("alg", "HS256")
                .subject("42")
                .claim("email", "user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        var authentication = new JwtAuthenticationToken(
                jwt,
                java.util.List.of(
                        new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_USER")));

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(TenantContext.hasTenant()).isFalse();
        };

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.hasTenant()).isFalse();
    }


    @Test
    void reusedThreadDoesNotLeakPreviousTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // First request: authenticated tenant-scoped JWT.
        Jwt tenantJwt = Jwt.withTokenValue("tenant-token")
                .header("alg", "HS256")
                .subject("42")
                .claim("tenant_id", "7")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        var tenantContext = SecurityContextHolder.createEmptyContext();
        tenantContext.setAuthentication(new JwtAuthenticationToken(
                tenantJwt,
                java.util.List.of(
                        new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(tenantContext);

        filter.doFilter(request, response, (req, res) ->
                assertThat(TenantContext.getTenantId()).isEqualTo("7"));

        assertThat(TenantContext.hasTenant()).isFalse();

        // Second request: authenticated JWT with no tenant claim.
        Jwt userJwt = Jwt.withTokenValue("user-token")
                .header("alg", "HS256")
                .subject("42")
                .claim("email", "user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        var userContext = SecurityContextHolder.createEmptyContext();
        userContext.setAuthentication(new JwtAuthenticationToken(
                userJwt,
                java.util.List.of(
                        new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(userContext);

        filter.doFilter(request, response, (req, res) ->
                assertThat(TenantContext.hasTenant()).isFalse());

        assertThat(TenantContext.hasTenant()).isFalse();
    }

}
