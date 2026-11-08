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
        findByUsernameAndConversationId(
            String username,
            String conversationId
        );

    @Modifying
    @Query("""
    UPDATE PendingIssueDraftEntity d
    SET d.status = :creating
    WHERE d.draftId = :draftId
      AND d.username = :username
      AND d.conversationId = :conversationId
      AND d.status = :pending
    """)
    int claimDraft(
        UUID draftId,
        String username,
        String conversationId,
        Status pending,
        Status creating
    );

    int deleteByDraftIdAndUsernameAndConversationIdAndStatus(UUID id, String username, String conversationId, Status status);

}