
package com.example.pms.tenant.dto;

import com.example.pms.tenant.entity.TenantInvitation;
import com.example.pms.tenant.entity.TenantInvitationStatus;
import com.example.pms.tenant.entity.TenantRole;

import java.time.Instant;

public record InvitationResponse(
        Long id,
        String invitedEmail,
        TenantRole role,
        TenantInvitationStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant acceptedAt
) {
    public static InvitationResponse from(TenantInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getInvitedEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt()
        );
    }
}
