package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.persistence.TenantRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final Set<String> finflowUsernames;
    private final String brewlyUsername;

    public TenantService(
        TenantRepository tenantRepository,
        @Value("${app.tenancy.finflow-users}") String finflowUsers,
        @Value("${app.tenancy.brewly-user}") String brewlyUsername
    ) {
        this.tenantRepository = tenantRepository;

        this.finflowUsernames = Arrays.stream(finflowUsers.split(","))
            .map(String::trim)
            .filter(username -> !username.isBlank())
            .collect(Collectors.toUnmodifiableSet());

        this.brewlyUsername = brewlyUsername;
    }

    @Transactional(readOnly = true)
    public Tenant resolveBySlug(String slug) {
        return tenantRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow(() -> new IllegalStateException(
                "Tenant is unavailable: " + slug
            ));
    }

    @Transactional(readOnly = true)
    public Tenant resolveForUsername(String username) {

        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authenticated user required");
        }

        String tenantSlug;

        if (finflowUsernames.contains(username)) {
            tenantSlug = "finflow";
        } else if (brewlyUsername.equals(username)) {
            tenantSlug = "brewly";
        } else {
            throw new AccessDeniedException(
                "No tenant assigned to this user"
            );
        }

        return tenantRepository.findBySlugAndActiveTrue(tenantSlug)
            .orElseThrow(() -> new AccessDeniedException(
                "Tenant is unavailable"
            ));
    }
}
