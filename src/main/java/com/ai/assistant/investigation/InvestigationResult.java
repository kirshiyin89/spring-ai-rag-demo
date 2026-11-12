package com.ai.assistant.investigation;

import java.util.List;

public record InvestigationResult(
        InvestigationStatus status,
        String summary,
        List<String> documentationFindings,
        List<String> relatedIssues,
        String clarificationQuestion,
        String escalationReason
) {
}