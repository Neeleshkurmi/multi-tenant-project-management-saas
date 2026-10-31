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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;

@SpringBootTest
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

    @Test
    void concurrentAcceptanceAllowsOnlyOneSuccessfulAcceptance() throws Exception {
        var owner = register("owner-concurrent@example.com", "Owner Org");
        var invitee = register("invitee-concurrent@example.com", "Invitee Org");

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "invitee-concurrent@example.com",
                TenantRole.MEMBER
        );

        System.out.println("Invitation expires at: " + invitation.expiresAt());
        System.out.println("Current time: " + Instant.now());

        int requestCount = 2;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var failures = new AtomicInteger();
        var errors = new ConcurrentLinkedQueue<Throwable>();

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            Callable<Void> attemptAcceptance = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start");
                }

                try {
                    boolean accepted = invitationService.acceptInvitation(
                            invitee.userId(),
                            invitation.invitationToken()
                    );

                    if (accepted) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (IllegalArgumentException exception) {
                failures.incrementAndGet();
            } catch (Throwable exception) {
                errors.add(exception);
                throw exception;
            }

                return null;
            };

            Future<Void> first = executor.submit(attemptAcceptance);
            Future<Void> second = executor.submit(attemptAcceptance);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(errors)
                .withFailMessage("Concurrent acceptance errors: %s", errors)
                .isEmpty();

        System.out.println("Concurrent acceptance successes: " + successes.get());
        System.out.println("Concurrent acceptance failures: " + failures.get());
        System.out.println("Concurrent acceptance errors: " + errors);

        assertThat(errors).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        var membership = membershipRepository.findByUser_IdAndTenant_Id(
                invitee.userId(),
                owner.tenantId()
        );

        assertThat(membership).isPresent();

        var savedInvitation = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.ACCEPTED);
    }

    @Test
    void concurrentInvitationCreationAllowsOnlyOnePendingInvitation()
            throws Exception {

        var owner = register(
                "owner-concurrent-create@example.com",
                "Concurrent Create Org"
        );

        String invitedEmail = "concurrent-invitee@example.com";
        int requestCount = 2;

        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var duplicateRejections = new AtomicInteger();
        var errors = new ConcurrentLinkedQueue<Throwable>();

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        try {
            Callable<Void> attemptInvitation = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start"
                    );
                }

                try {
                    invitationService.createInvitation(
                            owner.userId(),
                            owner.tenantId(),
                            invitedEmail,
                            TenantRole.MEMBER
                    );

                    successes.incrementAndGet();

                } catch (IllegalArgumentException exception) {
                    if ("A pending invitation already exists for this email"
                            .equals(exception.getMessage())) {
                        duplicateRejections.incrementAndGet();
                    } else {
                        errors.add(exception);
                        throw exception;
                    }
                } catch (Throwable exception) {
                    errors.add(exception);
                    throw exception;
                }

                return null;
            };

            Future<Void> first = executor.submit(attemptInvitation);
            Future<Void> second = executor.submit(attemptInvitation);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }

        assertThat(errors)
                .withFailMessage("Concurrent creation errors: %s", errors)
                .isEmpty();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicateRejections.get()).isEqualTo(1);

        var pendingInvitations =
                invitationRepository.findAllByTenant_IdAndStatus(
                        owner.tenantId(),
                        TenantInvitationStatus.PENDING
                );

        assertThat(pendingInvitations)
                .filteredOn(invitation ->
                        invitation.getInvitedEmail().equals(invitedEmail))
                .hasSize(1);
    }

    @Test
    void ownerCanListInvitations() {
        var owner = register(
                "owner-list@example.com",
                "List Org"
        );

        invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "first-invitee@example.com",
                TenantRole.MEMBER
        );

        invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "second-invitee@example.com",
                TenantRole.ADMIN
        );

        var invitations = invitationService.listInvitations(
                owner.userId(),
                owner.tenantId()
        );

        assertThat(invitations).hasSize(2);

        assertThat(invitations)
                .extracting("invitedEmail")
                .containsExactly(
                        "second-invitee@example.com",
                        "first-invitee@example.com"
                );
    }

    @Test
    void adminCanListInvitations() {
        var owner = register(
                "owner-admin-list@example.com",
                "Admin List Org"
        );

        var admin = register(
                "admin-list@example.com",
                "Admin Personal Org"
        );

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "admin-list@example.com",
                TenantRole.ADMIN
        );

        invitationService.acceptInvitation(
                admin.userId(),
                invitation.invitationToken()
        );

        invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "another-user@example.com",
                TenantRole.MEMBER
        );

        var invitations = invitationService.listInvitations(
                admin.userId(),
                owner.tenantId()
        );

        assertThat(invitations).hasSize(2);
    }

    @Test
    void regularMemberCannotListInvitations() {
        var owner = register(
                "owner-member-list@example.com",
                "Member List Org"
        );

        var member = register(
                "member-list@example.com",
                "Member Personal Org"
        );

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "member-list@example.com",
                TenantRole.MEMBER
        );

        invitationService.acceptInvitation(
                member.userId(),
                invitation.invitationToken()
        );

        assertThatThrownBy(() ->
                invitationService.listInvitations(
                        member.userId(),
                        owner.tenantId()
                )
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerCanRevokePendingInvitation() {
        var owner = register(
                "owner-revoke@example.com",
                "Revoke Org"
        );

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "revoke-invitee@example.com",
                TenantRole.MEMBER
        );

        invitationService.revokeInvitation(
                owner.userId(),
                owner.tenantId(),
                invitation.invitationId()
        );

        var savedInvitation = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.REVOKED);
    }

    @Test
    void regularMemberCannotRevokeInvitation() {
        var owner = register(
                "owner-member-revoke@example.com",
                "Member Revoke Org"
        );

        var member = register(
                "member-revoke@example.com",
                "Member Personal Org"
        );

        var membershipInvitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "member-revoke@example.com",
                TenantRole.MEMBER
        );

        invitationService.acceptInvitation(
                member.userId(),
                membershipInvitation.invitationToken()
        );

        var targetInvitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "target-revoke@example.com",
                TenantRole.MEMBER
        );

        assertThatThrownBy(() ->
                invitationService.revokeInvitation(
                        member.userId(),
                        owner.tenantId(),
                        targetInvitation.invitationId()
                )
        ).isInstanceOf(AccessDeniedException.class);

        var savedInvitation = invitationRepository
                .findById(targetInvitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.PENDING);
    }

    @Test
    void ownerCannotRevokeInvitationFromAnotherOrganisation() {
        var ownerOne = register(
                "owner-one-cross-revoke@example.com",
                "Organisation One"
        );

        var ownerTwo = register(
                "owner-two-cross-revoke@example.com",
                "Organisation Two"
        );

        var invitation = invitationService.createInvitation(
                ownerTwo.userId(),
                ownerTwo.tenantId(),
                "cross-revoke-invitee@example.com",
                TenantRole.MEMBER
        );

        assertThatThrownBy(() ->
                invitationService.revokeInvitation(
                        ownerOne.userId(),
                        ownerOne.tenantId(),
                        invitation.invitationId()
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invitation does not belong to this organisation");

        var savedInvitation = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.PENDING);
    }

    @Test
    void acceptedInvitationCannotBeRevoked() {
        var owner = register(
                "owner-accepted-revoke@example.com",
                "Accepted Revoke Org"
        );

        var invitee = register(
                "invitee-accepted-revoke@example.com",
                "Invitee Org"
        );

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "invitee-accepted-revoke@example.com",
                TenantRole.MEMBER
        );

        invitationService.acceptInvitation(
                invitee.userId(),
                invitation.invitationToken()
        );

        assertThatThrownBy(() ->
                invitationService.revokeInvitation(
                        owner.userId(),
                        owner.tenantId(),
                        invitation.invitationId()
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only pending invitations can be revoked");

        var savedInvitation = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.ACCEPTED);
    }

    @Test
    void alreadyRevokedInvitationCannotBeRevokedAgain() {
        var owner = register(
                "owner-double-revoke@example.com",
                "Double Revoke Org"
        );

        var invitation = invitationService.createInvitation(
                owner.userId(),
                owner.tenantId(),
                "double-revoke@example.com",
                TenantRole.MEMBER
        );

        invitationService.revokeInvitation(
                owner.userId(),
                owner.tenantId(),
                invitation.invitationId()
        );

        assertThatThrownBy(() ->
                invitationService.revokeInvitation(
                        owner.userId(),
                        owner.tenantId(),
                        invitation.invitationId()
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only pending invitations can be revoked");

        var savedInvitation = invitationRepository
                .findById(invitation.invitationId())
                .orElseThrow();

        assertThat(savedInvitation.getStatus())
                .isEqualTo(TenantInvitationStatus.REVOKED);
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