
package com.ai.assistant.investigation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvestigationDocumentationServiceImpl
    implements InvestigationDocumentationService {

  private final ChatClient.Builder chatClientBuilder;
  private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

  @Override
  public List<String> searchDocumentation(String question) {

    log.info("Searching documentation. questionLength={}",
        question == null ? 0 : question.length());
    log.debug("Documentation search question: {}", question);

    ChatClient chatClient = chatClientBuilder.clone().build();

    String finding = chatClient.prompt()
        .system("""
            You are an internal IT documentation retrieval assistant
            for FinFlow.

                Your task is to summarize only the information supported
            by the retrieved internal documentation.

                STRICT RULES:

            - Use only facts explicitly stated in retrieved documents.
                - Never invent contact details, email addresses, phone numbers,
                URLs, employee names, asset numbers, policies, or procedures.
                - Never fill missing information with general IT knowledge.
                - Do not assume that a common IT practice is a FinFlow policy.
                - Do not invent troubleshooting steps.
            - Do not recommend security-sensitive actions unless
            the retrieved documentation explicitly supports them.
            - Do not create a ticket or claim that one was created.
            - If documentation does not support an answer, say so.
                - Clearly distinguish documented procedures from
            information reported by the employee.

                Return a concise investigation finding.
            Include only information that the documentation supports.
            """)
        .user(question)
        .advisors(retrievalAugmentationAdvisor)
        .call()
        .content();

    if (finding == null || finding.isBlank()) {
      log.info("Documentation search returned no finding");
      return List.of();
    }

    log.info("Documentation search returned one finding. findingLength={}",
        finding.length());
    log.debug("Documentation finding: {}", finding);
    return List.of(finding);
  }
}
