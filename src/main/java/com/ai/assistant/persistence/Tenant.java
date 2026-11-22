package com.ai.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Data;

@Entity
@Data
@Table(
    name = "tenants",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_tenant_slug",
            columnNames = "slug"
        )
    }
)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "classifier_prompt", columnDefinition = "TEXT")
    private String classifierPrompt;

    @Column(name = "clarification_prompt", columnDefinition = "TEXT")
    private String clarificationPrompt;

    @Column(name = "out_of_scope_message", columnDefinition = "TEXT")
    private String outOfScopeMessage;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Returns a configured tenant setting, or fails fast. A missing value must never be
     * filled in from another tenant's configuration.
     */
    public String requireSetting(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Tenant " + slug + " has no " + settingName + " configured"
            );
        }
        return value;
    }
}
