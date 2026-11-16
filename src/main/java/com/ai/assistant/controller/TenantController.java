package com.ai.assistant.controller;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/current")
    public TenantInfo current(Authentication authentication) {

        Tenant tenant = tenantService.resolveForUsername(
            authentication.getName()
        );

        return new TenantInfo(
            tenant.getId(),
            tenant.getName(),
            tenant.getSlug()
        );
    }

    public record TenantInfo(
        Long id,
        String name,
        String slug
    ) {}
}
