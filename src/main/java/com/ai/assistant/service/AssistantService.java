package com.ai.assistant.service;

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

@Service
public class AssistantService {

  private final ChatClient chatClient;
  private final Resource systemPromptResource;
  private final Resource userPromptResource;
  private final VectorStore vectorStore;

  public AssistantService(
      ChatClient.Builder chatClientBuilder,
      ChatMemory chatMemory,
      @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
      @Value("classpath:/prompts/user-prompt.st") Resource userPromptResource,
      VectorStore vectorStore) {

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

    List<Document> documents = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query(question)
            .topK(2)
            .build()
    );

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