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
        String username,
        String conversationId,
        String employeeMessage
    ) {
        Instant now = Instant.now();

        PendingIssueDraftEntity entity = repository
            .findByUsernameAndConversationId(username, conversationId)
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

        entity.setUsername(username);
        entity.setConversationId(conversationId);
        entity.setStatus(Status.COLLECTING);
        entity.setUpdatedAt(now);

        return toPendingDraft(repository.save(entity));
    }

    @Transactional
    public PendingDraft save(
        String username,
        String conversationId,
        AssistantResponse.IssueDraft draft
    ) {
        Instant now = Instant.now();

        PendingIssueDraftEntity entity =
            repository.findByUsernameAndConversationId(
                username,
                conversationId
            ).orElseGet(PendingIssueDraftEntity::new);

        if (entity.getDraftId() == null) {
            entity.setDraftId(UUID.randomUUID());
            entity.setCreatedAt(now);
        }
        entity.setUsername(username);
        entity.setConversationId(conversationId);
        entity.setSummary(draft.summary());
        entity.setDescription(draft.description());
        entity.setStatus(Status.PENDING);

        entity.setUpdatedAt(now);

        return toPendingDraft(repository.save(entity));
    }

    public Optional<PendingDraft> get(
        String username,
        String conversationId
    ) {
        return repository
            .findByUsernameAndConversationId(
                username,
                conversationId
            )
            .map(this::toPendingDraft);
    }

    @Transactional
    public Optional<PendingDraft> claim(
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
            username,
            conversationId,
            Status.PENDING,
            Status.CREATING
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return get(username, conversationId);
    }

    @Transactional
    public boolean update(
        String username,
        String conversationId,
        String draftId,
        AssistantResponse.IssueDraft updatedDraft
    ) {
        Optional<PendingIssueDraftEntity> optionalEntity =
            repository.findByUsernameAndConversationId(
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
        String username,
        String conversationId,
        String draftId
    ) {
        return deleteDraft(
            username,
            conversationId,
            draftId,
            Status.PENDING
        );
    }

    @Transactional
    public boolean complete(
        String username,
        String conversationId,
        String draftId
    ) {
        return deleteDraft(
            username,
            conversationId,
            draftId,
            Status.CREATING
        );
    }

    @Transactional
    public void remove(
        String username,
        String conversationId,
        String draftId
    ) {
        try {
            UUID id = UUID.fromString(draftId);

            repository.deleteByDraftIdAndUsernameAndConversationIdAndStatus(
                id,
                username,
                conversationId,
                Status.PENDING
            );
        } catch (IllegalArgumentException ex) {
            // Invalid draft ID: nothing to remove.
        }
    }

    private boolean deleteDraft(
        String username,
        String conversationId,
        String draftId,
        Status status
    ) {
        try {
            UUID id = UUID.fromString(draftId);

            return repository
                .deleteByDraftIdAndUsernameAndConversationIdAndStatus(
                    id,
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