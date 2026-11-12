package com.ai.assistant.investigation;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Runs one bounded evidence-gathering pass for an employee request. */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvestigationOrchestrator {

    private final InvestigationDocumentationService documentationService;
    private final InvestigationQueryService queryService;
    private final InvestigationIssueService issueService;
    private final InvestigationDecisionService decisionService;
    private final InvestigationSessionStore sessionStore;

    public InvestigationResult investigate(InvestigationRequest request) {
        validateRequest(request);
        InvestigationSession previous = sessionStore.findByConversationId(request.conversationId())
            .orElse(null);
        boolean hasClarificationReply = previous != null
            && previous.status() == InvestigationSessionStatus.WAITING_FOR_CLARIFICATION;
        InvestigationRequest originalRequest = hasClarificationReply ? previous.request() : request;
        InvestigationContext context = hasClarificationReply ? copyContext(previous.context())
            : emptyContext();
        if (hasClarificationReply) {
            context.employeeResponses().add(request.question().trim());
        }

        String searchQuery = buildSearchQuery(originalRequest, context.employeeResponses());
        collectDocumentation(searchQuery, context);
        collectRelatedIssues(searchQuery, context);

        InvestigationResult result = decisionService.evaluate(originalRequest, context);
        if (hasClarificationReply && result.status() == InvestigationStatus.NEEDS_CLARIFICATION) {
            result = noFurtherClarificationResult(context);
        }

        InvestigationSessionStatus status = result.status() == InvestigationStatus.NEEDS_CLARIFICATION
            ? InvestigationSessionStatus.WAITING_FOR_CLARIFICATION
            : InvestigationSessionStatus.COMPLETED;
        sessionStore.save(new InvestigationSession(originalRequest, context, status,
            result.clarificationQuestion()));
        log.info("Investigation completed. conversationId={}, status={}, clarificationReply={}",
            request.conversationId(), result.status(), hasClarificationReply);
        return result;
    }

    private void collectDocumentation(String query, InvestigationContext context) {
        try {
            context.documentationFindings().addAll(documentationService.searchDocumentation(query));
            context.executedActions().add("SEARCH_DOCUMENTATION");
        } catch (Exception ex) {
            log.warn("Documentation search failed", ex);
            context.errors().add("Documentation search failed.");
        }
    }

    private void collectRelatedIssues(String query, InvestigationContext context) {
        try {
            List<String> queries = queryService.prepareYouTrackQueries(query);
            context.relatedIssues().addAll(issueService.searchRelatedIssues(queries));
            context.executedActions().add("SEARCH_YOUTRACK");
        } catch (Exception ex) {
            log.warn("YouTrack search failed", ex);
            context.errors().add("YouTrack search failed.");
        }
    }

    private InvestigationResult noFurtherClarificationResult(InvestigationContext context) {
        return new InvestigationResult(InvestigationStatus.ESCALATION_RECOMMENDED,
            "Thanks for the additional details. The available information has been reviewed.",
            List.copyOf(context.documentationFindings()), List.copyOf(context.relatedIssues()), null,
            "No further information is needed from you. If the suggested documented steps do not "
                + "resolve the problem, IT support should continue the investigation.");
    }

    private String buildSearchQuery(InvestigationRequest request, List<String> responses) {
        if (responses.isEmpty()) {
            return request.question().trim();
        }
        return request.question().trim() + "\nAdditional employee details: "
            + String.join(" ", responses);
    }

    private InvestigationContext emptyContext() {
        return new InvestigationContext(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>());
    }

    private InvestigationContext copyContext(InvestigationContext context) {
        return new InvestigationContext(new ArrayList<>(context.documentationFindings()),
            new ArrayList<>(context.relatedIssues()), new ArrayList<>(context.executedActions()),
            new ArrayList<>(context.errors()), new ArrayList<>(context.employeeResponses()));
    }

    private void validateRequest(InvestigationRequest request) {
        if (request == null || request.conversationId() == null || request.conversationId().isBlank()
            || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Conversation ID and question are required.");
        }
    }
}
