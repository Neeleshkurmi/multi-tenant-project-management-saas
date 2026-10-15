package com.example.pms.auth.integration;

import com.example.pms.auth.dto.LoginRequest;
import com.example.pms.auth.dto.LoginResponse;
import com.example.pms.auth.service.AuthService;
import com.example.pms.auth.service.LoginService;
import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.security.authentication.BadCredentialsException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LoginIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void registeredUserCanLoginAndReceiveValidJwt() {
//        authService.register(new RegisterRequest(
//                "login-test-2@example.com",
//                "StrongPassword123",
//                "Login Test Organization"));

        LoginResponse response = loginService.login(
                new LoginRequest(
                        "login-test@example.com",
                        "StrongPassword123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.userId()).isNotNull();
        assertThat(response.tenants()).hasSize(1);

        var jwt = jwtDecoder.decode(response.accessToken());

        assertThat(jwt.getSubject())
                .isEqualTo(response.userId().toString());
        assertThat(jwt.getClaimAsString("email"))
                .isEqualTo("login-test@example.com");
    }


    @Test
    void incorrectPasswordIsRejected() {
//        authService.register(new RegisterRequest(
//                "wrong-password@example.com",
//                "StrongPassword123",
//                "Wrong Password Organization"));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(
                        "wrong-password@example.com",
                        "IncorrectPassword123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void unknownEmailIsRejectedWithSameGenericMessage() {
        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(
                        "unknown-user@example.com",
                        "StrongPassword123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }


    @Test
    void disabledUserCannotLogin() {
//        authService.register(new RegisterRequest(
//                "disabled-user@example.com",
//                "StrongPassword123",
//                "Disabled User Organization"));

        var user = appUserRepository
                .findByEmail("disabled-user@example.com")
                .orElseThrow();

        user.disable();
        appUserRepository.save(user);

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(
                        "disabled-user@example.com",
                        "StrongPassword123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }


}