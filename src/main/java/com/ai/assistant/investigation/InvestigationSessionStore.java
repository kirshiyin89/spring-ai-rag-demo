package com.ai.assistant.investigation;

import java.util.Optional;

public interface InvestigationSessionStore {

    InvestigationSession save(InvestigationSession session);

    Optional<InvestigationSession> findByConversationId(
        String conversationId
    );

    void delete(String conversationId);
}
