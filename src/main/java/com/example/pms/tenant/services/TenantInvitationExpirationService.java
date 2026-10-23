package com.example.pms.tenant.services;

import com.example.pms.tenant.entity.TenantInvitation;
import com.example.pms.tenant.entity.TenantInvitationStatus;
import com.example.pms.tenant.exception.InvitationExpiredException;
import com.example.pms.tenant.repository.TenantInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TenantInvitationExpirationService {

    private final TenantInvitationRepository invitationRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfExpired(Long invitationId) {
        TenantInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invitation not found"
                ));

        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            return false;
        }

        Instant now = Instant.now(clock);

        // A future expiry means the invitation is still valid.
        if (now.isBefore(invitation.getExpiresAt())) {
            return false;
        }

        invitation.expire();
        invitationRepository.saveAndFlush(invitation);

        return true;
    }
}