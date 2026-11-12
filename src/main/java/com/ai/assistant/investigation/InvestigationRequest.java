package com.ai.assistant.investigation;

public record InvestigationRequest(
    String conversationId,
    String question
) {

}