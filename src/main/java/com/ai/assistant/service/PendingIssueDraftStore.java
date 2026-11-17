package com.ai.assistant.service;

import com.ai.assistant.persistence.PendingIssueDraftEntity;
import com.ai.assistant.persistence.PendingIssueDraftRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PendingIssueDraftStore {

    private final PendingIssueDraftRepository repository;

    /**
     * Starts (or resumes) collecting the details for an issue. A collecting
     * draft is persisted so the next answer to a clarification question stays
     * in the ticket-drafting flow.
     */
    @Transactional
    public PendingDraft startCollecting(
        Long tenantId,
        String username,
        String conversationId,
        String employeeMessage
    ) {
        Instant now = Instant.now();

        PendingIssueDraftEntity entity = repository
            .findByTenantIdAndUsernameAndConversationId(tenantId, username, conversationId)
            .orElseGet(PendingIssueDraftEntity::new);

        if (entity.getDraftId() != null && entity.getStatus() != Status.COLLECTING) {
            return toPendingDraft(entity);
        }

        if (entity.getDraftId() == null) {
            entity.setDraftId(UUID.randomUUID());
            entity.setCreatedAt(now);
            entity.setSummary("Issue details are still being collected");
            entity.setDescription("Employee-reported details:\n" + employeeMessage);
        } else {
            entity.setDescription(entity.getDescription() + "\nEmployee follow-up:\n"
                + employeeMessage);
        }

        entity.setTenantId(tenantId);
        entity.setUsername(username);
        entity.setConversationId(conversationId);
        entity.setStatus(Status.COLLECTING);
        entity.setUpdatedAt(now);

        return toPendingDraft(repository.save(entity));
    }

    @Transactional
    public PendingDraft save(
        Long tenantId,
        String username,
        String conversationId,
        AssistantResponse.IssueDraft draft
    ) {
        Instant now = Instant.now();

        PendingIssueDraftEntity entity =
            repository.findByTenantIdAndUsernameAndConversationId(
                tenantId,
                username,
                conversationId
            ).orElseGet(PendingIssueDraftEntity::new);

        if (entity.getDraftId() == null) {
            entity.setDraftId(UUID.randomUUID());
            entity.setCreatedAt(now);
        }
        entity.setTenantId(tenantId);
        entity.setUsername(username);
        entity.setConversationId(conversationId);
        entity.setSummary(draft.summary());
        entity.setDescription(draft.description());
        entity.setStatus(Status.PENDING);

        entity.setUpdatedAt(now);

        return toPendingDraft(repository.save(entity));
    }

    public Optional<PendingDraft> get(
        Long tenantId,
        String username,
        String conversationId
    ) {
        return repository
            .findByTenantIdAndUsernameAndConversationId(
                tenantId,
                username,
                conversationId
            )
            .map(this::toPendingDraft);
    }

    @Transactional
    public Optional<PendingDraft> claim(
        Long tenantId,
        String username,
        String conversationId,
        String draftId
    ) {
        UUID id;

        try {
            id = UUID.fromString(draftId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        int updated = repository.claimDraft(
            id,
            tenantId,
            username,
            conversationId,
            Status.PENDING,
            Status.CREATING
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return get(tenantId, username, conversationId);
    }

    @Transactional
    public boolean update(
        Long tenantId,
        String username,
        String conversationId,
        String draftId,
        AssistantResponse.IssueDraft updatedDraft
    ) {
        Optional<PendingIssueDraftEntity> optionalEntity =
            repository.findByTenantIdAndUsernameAndConversationId(
                tenantId,
                username,
                conversationId
            );

        if (optionalEntity.isEmpty()) {
            return false;
        }

        PendingIssueDraftEntity entity = optionalEntity.get();

        if (!entity.getDraftId().toString().equals(draftId)
            || entity.getStatus() != Status.PENDING) {
            return false;
        }

        entity.setSummary(updatedDraft.summary());
        entity.setDescription(updatedDraft.description());
        entity.setUpdatedAt(Instant.now());

        repository.save(entity);

        return true;
    }

    @Transactional
    public boolean cancel(
        Long tenantId,
        String username,
        String conversationId,
        String draftId
    ) {
        return deleteDraft(
            tenantId,
            username,
            conversationId,
            draftId,
            Status.PENDING
        );
    }

    @Transactional
    public boolean complete(
        Long tenantId,
        String username,
        String conversationId,
        String draftId
    ) {
        return deleteDraft(
            tenantId,
            username,
            conversationId,
            draftId,
            Status.CREATING
        );
    }

    @Transactional
    public void remove(
        Long tenantId,
        String username,
        String conversationId,
        String draftId
    ) {
        try {
            UUID id = UUID.fromString(draftId);

            repository.deleteByDraftIdAndTenantIdAndUsernameAndConversationIdAndStatus(
                id,
                    tenantId,
                username,
                conversationId,
                Status.PENDING
            );
        } catch (IllegalArgumentException ex) {
            // Invalid draft ID: nothing to remove.
        }
    }

    private boolean deleteDraft(
        Long tenantId,
        String username,
        String conversationId,
        String draftId,
        Status status
    ) {
        try {
            UUID id = UUID.fromString(draftId);

            return repository
                .deleteByDraftIdAndTenantIdAndUsernameAndConversationIdAndStatus(
                    id,
                    tenantId,
                    username,
                    conversationId,
                    status
                ) == 1;

        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private PendingDraft toPendingDraft(
        PendingIssueDraftEntity entity
    ) {
        return new PendingDraft(
            entity.getDraftId().toString(),
            entity.getUsername(),
            entity.getConversationId(),
            new AssistantResponse.IssueDraft(
                entity.getSummary(),
                entity.getDescription()
            ),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

    public enum Status {
        COLLECTING,
        PENDING,
        CREATING
    }

    public record PendingDraft(
        String draftId,
        String username,
        String conversationId,
        AssistantResponse.IssueDraft draft,
        Status status,
        Instant createdAt
    ) {
    }
}