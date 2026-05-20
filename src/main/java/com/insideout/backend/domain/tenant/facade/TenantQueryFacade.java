package com.insideout.backend.domain.tenant.facade;

import com.insideout.backend.domain.tenant.repository.TenantMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantQueryFacade {

    private final TenantMembershipRepository tenantMembershipRepository;

    public Optional<UUID> findPrimaryTenantIdByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        return tenantMembershipRepository.findFirstByUser_Id(userId)
                .map(membership -> membership.getTenant().getId());
    }
}
