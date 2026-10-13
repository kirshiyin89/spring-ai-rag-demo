package com.ai.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
  private final Resource systemPromptResource;
  private final Resource userPromptResource;
  private final VectorStore vectorStore;
  private final QueryCategoryService queryCategoryService;

  public AssistantService(
      ChatClient.Builder chatClientBuilder,
      ChatMemory chatMemory,
      @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
      @Value("classpath:/prompts/user-prompt.st") Resource userPromptResource,
      VectorStore vectorStore, QueryCategoryService queryCategoryService) {
    this.queryCategoryService = queryCategoryService;

    this.chatClient = chatClientBuilder
        .defaultAdvisors(
            MessageChatMemoryAdvisor.builder(chatMemory).build()
        )
        .build();

    this.systemPromptResource = systemPromptResource;
    this.userPromptResource = userPromptResource;
    this.vectorStore = vectorStore;
  }

  public String askWithRag(
      String conversationId,
      String question) {

    String category = queryCategoryService.classify(question);

    if ("CLARIFY".equalsIgnoreCase(category)) {
      return "What do you need help with? For example, "
          + "GitLab access, company VPN access, "
          + "or something else?";
    }

    SearchRequest.Builder searchRequest = SearchRequest.builder()
        .query(question)
        .topK(10)
        .similarityThreshold(0.35);

   if (category != null
        && !category.isBlank()
        && !category.equals("NONE")) {

      searchRequest.filterExpression(
          "category == '" + category + "'"
      );
    }

    log.info("QUESTION: {}", question);
    log.info("CLASSIFIED CATEGORY: {}", category);
    assert category != null;
    log.info("Using metadata filter: {}",
        !category.equals("NONE"));

    List<Document> documents = vectorStore.similaritySearch(
        searchRequest.build()
    );

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
}