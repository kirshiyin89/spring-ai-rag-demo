package com.ai.assistant.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlugAndActiveTrue(String slug);

    /** Active tenants that have a website for the nightly crawler to index. */
    List<Tenant> findAllByCrawlStartUrlIsNotNullAndActiveTrue();
}
