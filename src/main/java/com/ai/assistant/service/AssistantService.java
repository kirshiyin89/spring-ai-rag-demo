package com.ai.assistant.service;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import java.util.List;

@Slf4j
@Service
public class AssistantService {

  private final ChatClient chatClient;
  private final ChatClient clarificationClient;
  private final ChatMemory chatMemory;
  private final Resource systemPromptResource;
  private final QueryCategoryService queryCategoryService;
  private final CompressionQueryTransformer compressionQueryTransformer;
  private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;


  public AssistantService(
      ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
      @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
      QueryCategoryService queryCategoryService,
      CompressionQueryTransformer compressionQueryTransformer,
      RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) {
    this.chatMemory = chatMemory;
    this.queryCategoryService = queryCategoryService;

    this.clarificationClient = chatClientBuilder.clone().build();
    this.compressionQueryTransformer = compressionQueryTransformer;
    this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;

    this.chatClient = chatClientBuilder
        .defaultAdvisors(
            MessageChatMemoryAdvisor.builder(chatMemory).build()
        )
        .build();

    this.systemPromptResource = systemPromptResource;
  }


  public String askWithRag(
      String conversationId,
      String question) {

    List<Message> history = chatMemory.get(conversationId);

    log.info("History messages: {}", history.size());
    log.info("Original question: {}", question);

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
    log.info("Classified category: {}", category);

    if ("CLARIFY".equalsIgnoreCase(category)) {
      return generateClarification(question);
    }

    log.info("Standalone question: {}", standaloneQuestion);

    String systemPrompt = new PromptTemplate(systemPromptResource)
        .render();

    return chatClient.prompt()
        .system(systemPrompt)
        .user(standaloneQuestion)
        .advisors(retrievalAugmentationAdvisor)
        .advisors(advisorSpec ->
            advisorSpec.param(
                ChatMemory.CONVERSATION_ID,
                conversationId
            )
        )
        .call()
        .content();
  }


  private String generateClarification(String question) {
    return Objects.requireNonNull(clarificationClient.prompt()
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