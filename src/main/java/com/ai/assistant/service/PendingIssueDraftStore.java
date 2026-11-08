package com.ai.assistant.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class PendingIssueDraftStore {

    private final Map<String, PendingDraft> drafts =
        new ConcurrentHashMap<>();

    public PendingDraft save(
        String username,
        String conversationId,
        AssistantResponse.IssueDraft draft
    ) {
        String key = key(username, conversationId);

        PendingDraft pending = new PendingDraft(
            UUID.randomUUID().toString(),
            username,
            conversationId,
            draft,
            Status.PENDING,
            Instant.now()
        );

        drafts.put(key, pending);
        return pending;
    }

    public Optional<PendingDraft> get(
        String username,
        String conversationId
    ) {
        return Optional.ofNullable(drafts.get(key(username, conversationId)));
    }

    public Optional<PendingDraft> claim(
        String username,
        String conversationId,
        String draftId
    ) {
        String key = key(username, conversationId);

        AtomicReference<PendingDraft> claimed = new AtomicReference<>();

        drafts.computeIfPresent(key, (ignored, current) -> {
            if (!current.draftId().equals(draftId)
                || current.status() != Status.PENDING) {
                return current;
            }

            PendingDraft updated = current.withStatus(Status.CREATING);

            claimed.set(updated);

            return updated;
        });

        return Optional.ofNullable(claimed.get());
    }

    public void remove(
        String username,
        String conversationId,
        String draftId
    ) {
        String key = key(username, conversationId);

        drafts.computeIfPresent(key, (ignored, current) ->
            current.draftId().equals(draftId) ? null : current);
    }

    public boolean update(
        String username,
        String conversationId,
        String draftId,
        AssistantResponse.IssueDraft updatedDraft) {

        String key = key(username, conversationId);
        AtomicBoolean updated = new AtomicBoolean(false);

        drafts.computeIfPresent(key, (k, current) -> {
            if (current.draftId().equals(draftId)
                && current.status() == Status.PENDING) {

                updated.set(true);

                return new PendingDraft(
                    current.draftId(),
                    current.username(),
                    current.conversationId(),
                    updatedDraft,
                    Status.PENDING,
                    Instant.now()
                );
            }

            return current;
        });

        return updated.get();
    }

    private String key(String username, String conversationId) {
        return username + ":" + conversationId;
    }

    public enum Status {
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
        PendingDraft withStatus(Status newStatus) {
            return new PendingDraft(
                draftId,
                username,
                conversationId,
                draft,
                newStatus,
                createdAt
            );
        }
    }

    public boolean cancel(
        String username,
        String conversationId,
        String draftId
    ) {
        String key = key(username, conversationId);

        PendingDraft current = drafts.get(key);

        if (current == null
            || !current.draftId().equals(draftId)
            || current.status() != Status.PENDING) {
            return false;
        }

        return drafts.remove(key, current);
    }

    public boolean complete(
        String username,
        String conversationId,
        String draftId
    ) {
        String key = key(username, conversationId);

        PendingDraft current = drafts.get(key);

        if (current == null
            || !current.draftId().equals(draftId)
            || current.status() != Status.CREATING) {
            return false;
        }

        return drafts.remove(key, current);
    }
}
