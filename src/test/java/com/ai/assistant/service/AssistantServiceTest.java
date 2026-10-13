package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.io.Resource;

class AssistantServiceTest {

  private ChatClient chatClient;
  private QueryCategoryService queryCategoryService;
  private DocumentRetrievalService documentRetrievalService;
  private AssistantService assistantService;

  @BeforeEach
  void setUp() {
    ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
    chatClient = mock(ChatClient.class);
    ChatMemory chatMemory = mock(ChatMemory.class);

    queryCategoryService = mock(QueryCategoryService.class);
    documentRetrievalService = mock(DocumentRetrievalService.class);

    when(chatClientBuilder.defaultAdvisors(any(Advisor[].class)))
        .thenReturn(chatClientBuilder);

    when(chatClientBuilder.build()).thenReturn(chatClient);

    assistantService = new AssistantService(
        chatClientBuilder,
        chatMemory,
        mock(Resource.class),
        mock(Resource.class),
        queryCategoryService,
        documentRetrievalService
    );
  }

  @Test
  void shouldReturnFallbackWhenNoDocumentsAreRetrieved() {
    String question = "What is the annual training budget?";

    when(queryCategoryService.classify(question))
        .thenReturn("hr");

    when(documentRetrievalService.retrieve(question, "hr"))
        .thenReturn(List.of());

    String answer = assistantService.askWithRag(
        "alice",
        question
    );

    assertThat(answer)
        .contains("I couldn't find relevant information")
        .contains("available FinFlow documentation");

    verify(queryCategoryService).classify(question);

    verify(documentRetrievalService)
        .retrieve(question, "hr");

    verify(chatClient, never()).prompt();
  }


  @Test
  void shouldAskForClarificationWithoutRetrievingDocuments() {
    String question = "How do I get access?";

    when(queryCategoryService.classify(question))
        .thenReturn("CLARIFY");

    String answer = assistantService.askWithRag(
        "alice",
        question
    );

    assertThat(answer)
        .contains("What do you need help with?")
        .contains("GitLab access")
        .contains("company VPN access");

    verify(queryCategoryService).classify(question);

    verify(documentRetrievalService, never())
        .retrieve(any(String.class), any(String.class));

    verify(chatClient, never()).prompt();
  }

}