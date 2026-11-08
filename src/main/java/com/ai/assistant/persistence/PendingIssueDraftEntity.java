package com.ai.assistant.persistence;

import com.ai.assistant.service.PendingIssueDraftStore.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Data
@Table(
    name = "pending_issue_drafts",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_draft_user_conversation",
        columnNames = {"username", "conversation_id"}
    )
)
public class PendingIssueDraftEntity {

    @Id
    private UUID draftId;

    @Column(nullable = false)
    private String username;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}