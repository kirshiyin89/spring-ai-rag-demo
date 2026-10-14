package com.ai.assistant.service;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AssistantService {

  private final ChatClient chatClient;
  private final ChatClient rewriteClient;
  private final ChatMemory chatMemory;
  private final Resource systemPromptResource;
  private final Resource userPromptResource;
  private final QueryCategoryService queryCategoryService;
  private final DocumentRetrievalService documentRetrievalService;
  private final RewriteQueryTransformer rewriteQueryTransformer;
  private final CompressionQueryTransformer compressionQueryTransformer;


  public AssistantService(
      ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
      @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
      @Value("classpath:/prompts/user-prompt.st") Resource userPromptResource,
      QueryCategoryService queryCategoryService,
      DocumentRetrievalService documentRetrievalService,
      RewriteQueryTransformer rewriteQueryTransformer,
      CompressionQueryTransformer compressionQueryTransformer) {
    this.chatMemory = chatMemory;
    this.queryCategoryService = queryCategoryService;
    this.documentRetrievalService = documentRetrievalService;

    this.rewriteClient = chatClientBuilder.clone().build();
    this.rewriteQueryTransformer = rewriteQueryTransformer;
    this.compressionQueryTransformer = compressionQueryTransformer;

    this.chatClient = chatClientBuilder
        .defaultAdvisors(
            MessageChatMemoryAdvisor.builder(chatMemory).build()
        )
        .build();

    this.systemPromptResource = systemPromptResource;
    this.userPromptResource = userPromptResource;
  }

  public String askWithRag(
      String conversationId,
      String question) {

    List<Message> history = chatMemory.get(conversationId);

    String standaloneQuestion = question;

    if (!history.isEmpty()) {
      Query queryWithHistory = Query.builder()
          .text(question)
          .history(history)
          .build();

      standaloneQuestion = compressionQueryTransformer
          .transform(queryWithHistory)
          .text();
    }

    String category = queryCategoryService.classify(standaloneQuestion);

    if ("CLARIFY".equalsIgnoreCase(category)) {
      return generateClarification(question);
    }

    String rewrittenQuestion = rewriteQueryTransformer
        .transform(new Query(standaloneQuestion))
        .text();

    log.info("Original question: {}", question);
    log.info("Standalone question: {}", standaloneQuestion);
    log.info("Rewritten query: {}", rewrittenQuestion);

    List<Document> documents =
        documentRetrievalService.retrieve(rewrittenQuestion, category);

    if (documents.isEmpty()) {
      return "I couldn't find relevant information in the "
          + "available FinFlow documentation. "
          + "Please provide more details or contact "
          + "the relevant team.";
    }

    documents.forEach(document -> {
      log.info("Similarity score: {}", document.getScore());
      log.info("Metadata: {}", document.getMetadata());
      log.info("Text: {}", document.getText());
      log.info("---");
    });

    String context = documents.stream()
        .map(Document::getText)
        .collect(Collectors.joining("\n\n---\n\n"));

    String userPrompt = new PromptTemplate(userPromptResource)
        .render(Map.of(
            "context", context,
            "question", question
        ));

    String systemPrompt = new PromptTemplate(systemPromptResource)
        .render();

    return chatClient.prompt()
        .system(systemPrompt)
        .user(userPrompt)
        .advisors(advisor -> advisor.param(
            ChatMemory.CONVERSATION_ID,
            conversationId
        ))
        .call()
        .content();
  }

  private String generateClarification(String question) {
    return Objects.requireNonNull(rewriteClient.prompt()
            .system("""
                You are a friendly assistant for FinFlow Technologies,
                helping employees with internal company documentation.
                
                The employee's request is ambiguous or lacks enough
                detail to identify what they need.
                
                Ask one short, natural follow-up question that helps
                identify their intent.
                
                Use the employee's original wording and context.
                Offer relevant examples from these topics when useful:
                onboarding, GitLab access, VPN and WLAN, hardware
                requests, software licenses, and company information.
                
                Do not answer the original question.
                Do not invent company policies or procedures.
                Do not ask multiple questions at once.
                """)
            .user(question)
            .call()
            .content())
        .trim();
  }
}