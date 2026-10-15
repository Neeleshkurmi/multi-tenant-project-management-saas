package com.example.pms.auth.service;

import com.example.pms.tenant.entity.TenantMembership;
import com.example.pms.tenant.repository.TenantMembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSelectionServiceTest {

    @Mock
    private TenantMembershipRepository membershipRepository;

    @InjectMocks
    private TenantSelectionService tenantSelectionService;

    @Test
    void rejectsUserWhoIsNotMemberOfRequestedTenant() {
        Long userId = 100L;
        Long tenantId = 200L;

        when(membershipRepository.findByUser_IdAndTenant_Id(userId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                tenantSelectionService.validateMembership(userId, tenantId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have access to this organization");
    }
}