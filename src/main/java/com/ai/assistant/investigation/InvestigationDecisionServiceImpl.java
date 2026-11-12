
package com.ai.assistant.investigation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InvestigationDecisionServiceImpl
    implements InvestigationDecisionService {

    private final ChatClient chatClient;

    public InvestigationDecisionServiceImpl(ChatClient.Builder builder) {
        this.chatClient = builder.clone().build();
    }

    @Override
    public InvestigationResult evaluate(
        InvestigationRequest request,
        InvestigationContext context) {

        Objects.requireNonNull(request, "Investigation request is required.");
        Objects.requireNonNull(context, "Investigation context is required.");

        log.info(
            "Evaluating investigation. conversationId={}, documentationFindings={}, relatedIssues={}",
            request.conversationId(),
            context.documentationFindings().size(),
            context.relatedIssues().size()
        );

        try {
            InvestigationResult assessment = chatClient.prompt()
                .system("""
                            You are an investigation decision assistant for FinFlow Technologies.

                            Your task is to evaluate the evidence gathered during an investigation
                            and decide its outcome.

                            Choose exactly one of these statuses:

                            1. INVESTIGATED
                               Use this only when the evidence identifies a sufficiently clear
                               cause and provides a supported explanation or actionable resolution.

                            2. NEEDS_CLARIFICATION
                               Use this when essential information is still missing and the employee
                               must answer a specific question before the investigation can continue.
                               Always provide a clarificationQuestion.

                            3. ESCALATION_RECOMMENDED
                               Use this when the problem remains unresolved, requires IT intervention,
                               or the available evidence does not support a reliable diagnosis.
                               Provide a clear escalationReason.

                            4. ESCALATION_NOT_NEEDED
                               Use this when the evidence shows that no escalation is necessary,
                               for example, when the employee has already resolved the problem.

                            IMPORTANT RULES:

                            - Documentation instructions are not proof that the problem is resolved.
                            - A recommendation to contact the IT Service Desk means that escalation
                              is recommended when no confirmed resolution exists.
                            - Do not claim that a problem has been fixed unless the evidence confirms it.
                            - Do not invent causes, solutions, incidents, or YouTrack findings.
                            - Consider the employee's clarification responses.
                            - Ask for clarification only when an essential detail is missing.
                            - If the employee has provided clarification responses, do not ask another
                              question. Give the most useful supported next action instead.
                            - Prefer a practical suggested action or escalation over clarification.
                            - Consider errors encountered during the investigation.
                            - An empty related-issues list means that no matching YouTrack evidence
                              was found. Do not interpret it as proof that no previous incidents exist.
                            - If documentation provides troubleshooting steps but the employee still
                              experiences the problem, do not mark the investigation as resolved.
                            - Keep the summary concise, plain-language, and explain the actual findings.
                            - Return a structured InvestigationResult.
                            - The documentationFindings and relatedIssues fields will be populated
                              by the application. You do not need to reproduce their contents.
                            """)
                .user(user -> user.text("""
                            Evaluate this investigation.

                            Original employee question:
                            {question}

                            Documentation findings:
                            {documentationFindings}

                            Related YouTrack issues:
                            {relatedIssues}

                            Employee clarification responses:
                            {employeeResponses}

                            Executed investigation actions:
                            {executedActions}

                            Investigation errors:
                            {errors}

                            Return your assessment using the InvestigationResult structure.
                            """)
                    .param("question", request.question())
                    .param("documentationFindings", context.documentationFindings())
                    .param("relatedIssues", context.relatedIssues())
                    .param("employeeResponses", context.employeeResponses())
                    .param("executedActions", context.executedActions())
                    .param("errors", context.errors()))
                .call()
                .entity(InvestigationResult.class);

            if (assessment == null || assessment.status() == null) {
                log.warn("The decision model returned an invalid assessment.");
                return createEscalationResult(
                    context,
                    "The investigation could not produce a reliable assessment."
                );
            }

            /*
             * Additional safeguard:
             * If the documentation explicitly directs the employee to IT
             * and YouTrack returned no related issues, do not report the
             * investigation as successfully resolved.
             */
            if (context.relatedIssues().isEmpty()
                && containsServiceDeskInstruction(context.documentationFindings())
                && assessment.status() != InvestigationStatus.NEEDS_CLARIFICATION) {

                log.info(
                    "No related YouTrack issues found and documentation recommends IT support."
                );

                return createEscalationResult(
                    context,
                    "The documentation directs the employee to contact the IT Service Desk. "
                        + "No related YouTrack issues provided a confirmed resolution."
                );
            }

            String escalationReason = assessment.escalationReason();

            if (assessment.status() == InvestigationStatus.ESCALATION_RECOMMENDED
                && (escalationReason == null || escalationReason.isBlank())) {
                escalationReason =
                    "The available evidence does not provide a confirmed resolution. "
                        + "IT support should investigate further.";
            }

            if (assessment.status() == InvestigationStatus.NEEDS_CLARIFICATION
                && (assessment.clarificationQuestion() == null
                || assessment.clarificationQuestion().isBlank())) {

                return createEscalationResult(
                    context,
                    "The decision model requested clarification but did not provide a question."
                );
            }

            InvestigationResult result = new InvestigationResult(
                assessment.status(),
                assessment.summary(),
                context.documentationFindings(),
                context.relatedIssues(),
                assessment.clarificationQuestion(),
                escalationReason
            );

            log.info(
                "Investigation decision: conversationId={}, status={}",
                request.conversationId(),
                result.status()
            );

            return result;

        } catch (Exception ex) {
            log.error("Failed to evaluate investigation.", ex);

            return createEscalationResult(
                context,
                "The investigation decision could not be completed. "
                    + "IT support should review the collected findings."
            );
        }
    }

    private boolean containsServiceDeskInstruction(List<String> findings) {

        if (findings == null) {
            return false;
        }

        return findings.stream()
            .filter(Objects::nonNull)
            .map(text -> text.toLowerCase(Locale.ROOT))
            .anyMatch(text ->
                text.contains("it service desk")
                    || text.contains("contact it support")
                    || text.contains("contact the it team")
            );
    }

    private InvestigationResult createEscalationResult(
        InvestigationContext context,
        String reason) {

        return new InvestigationResult(
            InvestigationStatus.ESCALATION_RECOMMENDED,
            "The investigation did not identify a confirmed resolution. "
                + "IT support should investigate further.",
            context.documentationFindings(),
            context.relatedIssues(),
            null,
            reason
        );
    }
}
