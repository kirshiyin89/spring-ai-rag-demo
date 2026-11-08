package com.ai.assistant.service;

public record AssistantResponse(
    ResponseType type,
    String message,
    String draftId,
    IssueDraft draft
) {

    public enum ResponseType {
        ANSWER,
        CLARIFICATION,
        ISSUE_DRAFT,
        APPROVAL_REQUIRED,
        ISSUE_CREATED,
        CANCELLED
    }

    public record IssueDraft(
        String summary,
        String description
    ) {
    }

    public record DraftActionResponse(
        DraftAction action,
        String message,
        IssueDraft updatedDraft
    ) {
    }

    public enum DraftAction {
        APPROVE,
        CANCEL,
        EDIT,
        ASK_QUESTION,
        KEEP_PENDING
    }
}