package com.example.pms.tenant.controller;

import com.example.pms.tenant.dto.InvitationResponse;
import com.example.pms.tenant.entity.TenantRole;
import com.example.pms.tenant.exception.InvitationExpiredException;
import com.example.pms.tenant.services.TenantInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tenant-invitations")
@RequiredArgsConstructor
public class TenantInvitationController {

    private final TenantInvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateInvitationResponse createInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInvitationRequest request
    ) {
        Long userId = extractUserId(jwt);

        TenantInvitationService.InvitationCreatedResult result =
                invitationService.createInvitation(
                        userId,
                        request.tenantId(),
                        request.email(),
                        request.role()
                );

        return new CreateInvitationResponse(
                result.invitationId(),
                result.invitedEmail(),
                result.role(),
                result.expiresAt(),
                result.invitationToken()
        );
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> acceptInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AcceptInvitationRequest request
    ) {
        Long userId = extractUserId(jwt);

        boolean accepted = invitationService.acceptInvitation(
                userId,
                request.token()
        );

        if (!accepted) {
            throw new InvitationExpiredException();
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<InvitationResponse>> listInvitations(
            @RequestParam Long tenantId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = extractUserId(jwt);

        return ResponseEntity.ok(
                invitationService.listInvitations(userId, tenantId)
        );
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable Long invitationId,
            @RequestParam Long tenantId,
            @AuthenticationPrincipal Long userId
    ) {
        invitationService.revokeInvitation(
                userId,
                tenantId,
                invitationId
        );

        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authentication is required"
            );
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Invalid authenticated user"
            );
        }
    }

    public record CreateInvitationRequest(
            @NotNull @Positive Long tenantId,
            @NotBlank @Email String email,
            @NotNull TenantRole role
    ) {}

    public record AcceptInvitationRequest(
            @NotBlank String token
    ) {}

    public record CreateInvitationResponse(
            Long invitationId,
            String invitedEmail,
            TenantRole role,
            Instant expiresAt,
            String invitationToken
    ) {}
}