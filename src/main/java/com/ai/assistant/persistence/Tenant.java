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

    /** Whether the ticket workflows (drafts, status changes, YouTrack lookups) are enabled. */
    @Column(name = "tickets_enabled", nullable = false)
    private boolean ticketsEnabled;

    /** Whether the anonymous public chat endpoint answers for this tenant. */
    @Column(name = "public_chat_enabled", nullable = false)
    private boolean publicChatEnabled;

    /** Start URL for the website crawler. Null when the tenant has no website to crawl. */
    @Column(name = "crawl_start_url", length = 500)
    private String crawlStartUrl;

    /** Value stored as the "source" metadata of this tenant's knowledge documents. */
    @Column(name = "knowledge_source", nullable = false, length = 100)
    private String knowledgeSource = "knowledge-base";

    /** Value stored as the "documentType" metadata of this tenant's knowledge documents. */
    @Column(name = "knowledge_document_type", nullable = false, length = 100)
    private String knowledgeDocumentType = "business-documentation";

    /**
     * JSON object keyed by document filename, with per-document metadata such as category
     * and department. The key "*" holds the defaults for documents without their own entry.
     */
    @Column(name = "document_metadata", columnDefinition = "TEXT")
    private String documentMetadata;

    /** Page a user of this tenant is sent to after logging in. Always a local path such as "/". */
    @Column(name = "landing_path", nullable = false, length = 200)
    private String landingPath = "/";

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
