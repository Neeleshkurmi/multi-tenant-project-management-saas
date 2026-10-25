package com.example.pms.tenant.services;

import com.example.pms.tenant.dto.InvitationResponse;
import com.example.pms.tenant.entity.Tenant;
import com.example.pms.tenant.entity.TenantInvitation;
import com.example.pms.tenant.entity.TenantInvitationStatus;
import com.example.pms.tenant.entity.TenantMembership;
import com.example.pms.tenant.entity.TenantRole;
import com.example.pms.tenant.repository.InvitationLockRepository;
import com.example.pms.tenant.repository.TenantInvitationRepository;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import com.example.pms.tenant.repository.TenantRepository;
import com.example.pms.user.entity.AppUser;
import com.example.pms.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import com.example.pms.tenant.exception.InvitationExpiredException;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class TenantInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final InvitationLockRepository invitationLockRepository;

    private final TenantInvitationRepository invitationRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;
    private final TenantInvitationExpirationService expirationService;

    @Transactional
    public InvitationCreatedResult createInvitation(
            Long inviterUserId,
            Long tenantId,
            String invitedEmail,
            TenantRole role
    ) {
        if (inviterUserId == null || tenantId == null) {
            throw new IllegalArgumentException(
                    "User ID and organization ID are required"
            );
        }

        if (invitedEmail == null || invitedEmail.isBlank()) {
            throw new IllegalArgumentException("Invitee email is required");
        }

        if (role == null || role == TenantRole.OWNER) {
            throw new IllegalArgumentException(
                    "Invitations may grant ADMIN or MEMBER only"
            );
        }

        String normalizedEmail = invitedEmail.trim()
                .toLowerCase(Locale.ROOT);

        TenantMembership inviterMembership =
                membershipRepository.findByUser_IdAndTenant_Id(
                        inviterUserId, tenantId
                ).orElseThrow(() -> new AccessDeniedException(
                        "You are not a member of this organization"
                ));

        TenantRole inviterRole = inviterMembership.getRole();

        if (inviterRole != TenantRole.OWNER
                && inviterRole != TenantRole.ADMIN) {
            throw new AccessDeniedException(
                    "Only owners and admins can invite members"
            );
        }

        if (role == TenantRole.ADMIN
                && inviterRole != TenantRole.OWNER) {
            throw new AccessDeniedException(
                    "Only the organization owner can invite admins"
            );
        }

        AppUser inviter = inviterMembership.getUser();

        if (inviter.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "You cannot invite yourself"
            );
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found"
                ));

        AppUser existingUser = userRepository.findByEmail(normalizedEmail)
                .orElse(null);

        if (existingUser != null
                && membershipRepository
                .findByUser_IdAndTenant_Id(existingUser.getId(), tenantId)
                .isPresent()) {
            throw new IllegalArgumentException(
                    "This user is already a member of the organization"
            );
        }

        invitationLockRepository.createIfMissing(
                tenantId,
                normalizedEmail
        );

        invitationLockRepository.findForUpdate(
                tenantId,
                normalizedEmail
        ).orElseThrow(() -> new IllegalStateException(
                "Could not acquire invitation lock"
        ));

        boolean pendingInvitationExists =
                invitationRepository.findPendingForUpdate(
                        tenantId,
                        normalizedEmail,
                        TenantInvitationStatus.PENDING
                ).isPresent();

        if (pendingInvitationExists) {
            throw new IllegalArgumentException(
                    "A pending invitation already exists for this email"
            );
        }

        String rawToken = generateToken();
        String tokenHash = sha256(rawToken);

        TenantInvitation invitation = new TenantInvitation(
                tenant,
                normalizedEmail,
                inviter,
                tokenHash,
                role,
                Instant.now(clock).plus(7, ChronoUnit.DAYS)
        );

        invitationRepository.save(invitation);

        return new InvitationCreatedResult(
                invitation.getId(),
                normalizedEmail,
                role,
                invitation.getExpiresAt(),
                rawToken
        );
    }

    @Transactional
    public boolean acceptInvitation(Long userId, String rawToken) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String tokenHash = hashToken(rawToken);

        TenantInvitation invitation = invitationRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));

        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is no longer pending");
        }

        if (!user.getEmail().equalsIgnoreCase(invitation.getInvitedEmail())) {
            throw new AccessDeniedException(
                    "This invitation belongs to a different email address"
            );
        }

        System.out.println("Acceptance check - now: " + Instant.now(clock));
        System.out.println("Acceptance check - expiresAt: " + invitation.getExpiresAt());
        System.out.println("Acceptance check - status: " + invitation.getStatus());

        if (!Instant.now(clock).isBefore(invitation.getExpiresAt())) {
            invitation.expire();
            invitationRepository.saveAndFlush(invitation);
            return false;
        }

        if (membershipRepository.findByUser_IdAndTenant_Id(
                userId, invitation.getTenant().getId()
        ).isPresent()) {
            throw new IllegalArgumentException(
                    "User is already a member of this organization"
            );
        }

        TenantMembership membership = new TenantMembership(
                user,
                invitation.getTenant(),
                invitation.getRole()
        );

        membershipRepository.save(membership);
        invitation.accept(Instant.now(clock));
        invitationRepository.save(invitation);

        return true;
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitations(
            Long requesterUserId,
            Long tenantId
    ) {
        TenantMembership membership = membershipRepository
                .findByUser_IdAndTenant_Id(requesterUserId, tenantId)
                .orElseThrow(() ->
                        new org.springframework.security.access.AccessDeniedException(
                                "You are not a member of this organisation"
                        )
                );

        if (membership.getRole() != TenantRole.OWNER
                && membership.getRole() != TenantRole.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only owners and admins can view invitations"
            );
        }

        return invitationRepository
                .findAllByTenant_IdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to hash invitation token", exception
            );
        }
    }

    public record InvitationCreatedResult(
            Long invitationId,
            String invitedEmail,
            TenantRole role,
            Instant expiresAt,
            String invitationToken
    ) {}
}