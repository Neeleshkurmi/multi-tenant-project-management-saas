package com.example.pms.auth.controller;

import com.example.pms.auth.dto.LoginRequest;
import com.example.pms.auth.dto.LoginResponse;
import com.example.pms.auth.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(response);
    }
}