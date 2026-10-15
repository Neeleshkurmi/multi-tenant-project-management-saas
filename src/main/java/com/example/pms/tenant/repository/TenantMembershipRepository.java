
package com.example.pms.tenant.repository;

import com.example.pms.tenant.entity.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantMembershipRepository
        extends JpaRepository<TenantMembership, Long> {

    List<TenantMembership> findAllByUser_Id(Long userId);

    Optional<TenantMembership> findByUser_IdAndTenant_Id(
            Long userId,
            Long tenantId);
}
