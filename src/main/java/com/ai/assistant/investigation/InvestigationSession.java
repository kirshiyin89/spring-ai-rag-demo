package com.ai.assistant.investigation;

public record InvestigationSession(

    InvestigationRequest request,

    InvestigationContext context,

    InvestigationSessionStatus status,

    String clarificationQuestion

) {

    public String conversationId() {
        return request.conversationId();
    }
}
