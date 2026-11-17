package com.ai.assistant.persistence;

import com.ai.assistant.service.PendingIssueDraftStore.Status;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PendingIssueDraftRepository
    extends JpaRepository<PendingIssueDraftEntity, UUID> {

    Optional<PendingIssueDraftEntity>
        findByTenantIdAndUsernameAndConversationId(Long tenantId,
            String username,
            String conversationId
        );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
    UPDATE PendingIssueDraftEntity d
    SET d.status = :creating
    WHERE d.draftId = :draftId
      AND d.tenantId = :tenantId
      AND d.username = :username
      AND d.conversationId = :conversationId
      AND d.status = :pending
    """)
    int claimDraft(
        UUID draftId,
        Long tenantId,
        String username,
        String conversationId,
        Status pending,
        Status creating
    );

    int deleteByDraftIdAndTenantIdAndUsernameAndConversationIdAndStatus(UUID id, Long tenantId, String username, String conversationId, Status status);

}