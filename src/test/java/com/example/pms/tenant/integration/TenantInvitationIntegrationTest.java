package com.example.pms.tenant.integration;

import com.example.pms.auth.dto.RegisterRequest;
import com.example.pms.auth.service.AuthService;
import com.example.pms.tenant.entity.TenantInvitation;
import com.example.pms.tenant.entity.TenantInvitationStatus;
import com.example.pms.tenant.entity.TenantRole;
import com.example.pms.tenant.exception.InvitationExpiredException;
import com.example.pms.tenant.repository.TenantInvitationRepository;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import com.example.pms.tenant.services.TenantInvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TenantInvitationIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantInvitationService invitationService;

    @Autowired
    private TenantInvitationRepository invitationRepository;

    @Autowired
    private TenantMembershipRepository membershipRepository;

    @Test
    void ownerCanInviteUserAndInviteeCanAccept() {
        var owner = register("owner-invite@example.com", "Owner Org");
        var invitee = register("invitee@example.com", "Invitee Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "invitee@example.com",
                TenantRole.MEMBER
        );

        assertThat(invitation.invitationToken()).isNotBlank();
        assertThat(invitation.invitedEmail())
                .isEqualTo("invitee@example.com");

        invitationService.acceptInvitation(
                invitee.userId(),
                invitation.invitationToken()
        );

        var membership = membershipRepository
                .findByUser_IdAndTenant_Id(
                        invitee.userId(),
                        owner.tenantId()
                );

        assertThat(membership).isPresent();
        assertThat(membership.get().getRole()).isEqualTo(TenantRole.MEMBER);

        var savedInvitation = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.ACCEPTED);
    }

    @Test
    void userWithDifferentEmailCannotAcceptInvitation() {
        var owner = register("owner-wrong-email@example.com", "Owner Org");
        var invitee = register("wrong-email@example.com", "Other Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "someone-else@example.com",
                TenantRole.MEMBER
        );

        assertThatThrownBy(() -> invitationService.acceptInvitation(
                invitee.userId(),
                invitation.invitationToken()
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(membershipRepository
                .findByUser_IdAndTenant_Id(
                        invitee.userId(),
                        owner.tenantId()
                )).isEmpty();
    }

    @Test
    void invitationCannotBeAcceptedTwice() {
        var owner = register("owner-once@example.com", "Owner Org");
        var invitee = register("invitee-once@example.com", "Invitee Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "invitee-once@example.com",
                TenantRole.MEMBER
        );

        invitationService.acceptInvitation(
                invitee.userId(),
                invitation.invitationToken()
        );

        assertThatThrownBy(() -> invitationService.acceptInvitation(
                invitee.userId(),
                invitation.invitationToken()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memberCannotInviteAnotherUser() {
        var owner = register("owner-member-check@example.com", "Owner Org");
        var member = register("member-check@example.com", "Member Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "member-check@example.com",
                TenantRole.MEMBER
        );

        invitationService.acceptInvitation(
                member.userId(),
                invitation.invitationToken()
        );

        assertThatThrownBy(() -> invitationService.createInvitation(
                member.userId(),
                owner.tenantId(),
                "new-person@example.com",
                TenantRole.MEMBER
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCannotInviteAnotherAdmin() {
        var owner = register("owner-admin-check@example.com", "Owner Org");
        var admin = register("admin-check@example.com", "Admin Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "admin-check@example.com",
                TenantRole.ADMIN
        );

        invitationService.acceptInvitation(
                admin.userId(),
                invitation.invitationToken()
        );

        assertThatThrownBy(() -> invitationService.createInvitation(
                admin.userId(),
                owner.tenantId(),
                "another-admin@example.com",
                TenantRole.ADMIN
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void expiredInvitationCannotBeAccepted() {
        var owner = register("owner-expired@example.com", "Owner Org");
        var invitee = register("invitee-expired@example.com", "Invitee Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "invitee-expired@example.com",
                TenantRole.MEMBER
        );

        TenantInvitation saved = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        saved.setExpiresAtForTesting(
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        invitationRepository.saveAndFlush(saved);

        boolean accepted = invitationService.acceptInvitation(
                invitee.userId(),
                invitation.invitationToken()
        );

        assertThat(accepted).isFalse();

        TenantInvitation expired = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(expired.getStatus())
                .isEqualTo(TenantInvitationStatus.EXPIRED);

        assertThat(membershipRepository
                .findByUser_IdAndTenant_Id(
                        invitee.userId(),
                        owner.tenantId()
                )).isEmpty();
    }

    private Registration register(String email, String organizationName) {
        var response = authService.register(new RegisterRequest(
                email,
                "StrongPassword123",
                organizationName
        ));

        return new Registration(response.userId(), response.tenantId());
    }

    private record Registration(Long userId, Long tenantId) {}
}