package com.ai.assistant.service;

import com.ai.assistant.config.TenancyProperties;
import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.persistence.TenantRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    /** Tenant slug to its usernames, in configuration order. */
    private final Map<String, Set<String>> usernamesBySlug;

    public TenantService(TenantRepository tenantRepository, TenancyProperties tenancyProperties) {
        this.tenantRepository = tenantRepository;

        this.usernamesBySlug = new LinkedHashMap<>();
        tenancyProperties.users().forEach((slug, usernames) ->
            usernamesBySlug.put(slug, usernames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(username -> !username.isBlank())
                .collect(Collectors.toUnmodifiableSet())));
    }

    @Transactional(readOnly = true)
    public Tenant resolveBySlug(String slug) {
        return tenantRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow(() -> new IllegalStateException(
                "Tenant is unavailable: " + slug
            ));
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> findActiveBySlug(String slug) {
        return tenantRepository.findBySlugAndActiveTrue(slug);
    }

    /** Active tenants that have a website to crawl. */
    @Transactional(readOnly = true)
    public List<Tenant> findCrawlableTenants() {
        return tenantRepository.findAllByCrawlStartUrlIsNotNullAndActiveTrue();
    }

    @Transactional(readOnly = true)
    public Tenant resolveForUsername(String username) {

        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authenticated user required");
        }

        String tenantSlug = usernamesBySlug.entrySet().stream()
            .filter(entry -> entry.getValue().contains(username))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException(
                "No tenant assigned to this user"
            ));

        return tenantRepository.findBySlugAndActiveTrue(tenantSlug)
            .orElseThrow(() -> new AccessDeniedException(
                "Tenant is unavailable"
            ));
    }
}
