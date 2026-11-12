package com.ai.assistant.investigation;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InMemoryInvestigationSessionStore
        implements InvestigationSessionStore {

    private final ConcurrentMap<String, InvestigationSession> sessions =
        new ConcurrentHashMap<>();

    @Override
    public InvestigationSession save(InvestigationSession session) {

        if (session == null || session.conversationId() == null
                || session.conversationId().isBlank()) {
            throw new IllegalArgumentException(
                "Investigation session and conversation ID are required."
            );
        }

        sessions.put(session.conversationId(), session);

        log.info(
            "Investigation session saved. conversationId={}, status={}",
            session.conversationId(),
            session.status()
        );

        return session;
    }

    @Override
    public Optional<InvestigationSession> findByConversationId(
            String conversationId) {

        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        var session = Optional.ofNullable(
            sessions.get(conversationId)
        );

        log.debug(
            "Investigation session lookup. conversationId={}, found={}",
            conversationId,
            session.isPresent()
        );

        return session;
    }

    @Override
    public void delete(String conversationId) {

        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        sessions.remove(conversationId);

        log.info(
            "Investigation session deleted. conversationId={}",
            conversationId
        );
    }
}
