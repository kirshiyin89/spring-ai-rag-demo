//package com.ai.assistant.service;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//import java.util.List;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
//import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
//import org.springframework.ai.chat.client.advisor.api.Advisor;
//import org.springframework.ai.chat.memory.ChatMemory;
//import org.springframework.core.io.Resource;
//
//class AssistantServiceTest {
//
//  private ChatClient chatClient;
//  private ChatClient rewriteChatClient;
//  private ChatClient.ChatClientRequestSpec rewriteRequestSpec;
//  private ChatClient.CallResponseSpec rewriteResponseSpec;
//
//  private QueryCategoryService queryCategoryService;
//  private DocumentRetrievalService documentRetrievalService;
//  private AssistantService assistantService;
//
//  @BeforeEach
//  void setUp() {
//    ChatClient.Builder chatClientBuilder =
//        mock(ChatClient.Builder.class);
//    ChatClient.Builder rewriteClientBuilder =
//        mock(ChatClient.Builder.class);
//
//    chatClient = mock(ChatClient.class);
//    rewriteChatClient = mock(ChatClient.class);
//    ChatMemory chatMemory = mock(ChatMemory.class);
//
//    rewriteRequestSpec = mock(ChatClientRequestSpec.class);
//    rewriteResponseSpec = mock(CallResponseSpec.class);
//
//    queryCategoryService = mock(QueryCategoryService.class);
//    documentRetrievalService = mock(DocumentRetrievalService.class);
//
//    // The cloned builder creates the client without memory.
//    when(chatClientBuilder.clone())
//        .thenReturn(rewriteClientBuilder);
//    when(rewriteClientBuilder.build())
//        .thenReturn(rewriteChatClient);
//
//    // The original builder creates the client with memory.
//    when(chatClientBuilder.defaultAdvisors(any(Advisor[].class)))
//        .thenReturn(chatClientBuilder);
//    when(chatClientBuilder.build())
//        .thenReturn(chatClient);
//
//    // Mock the fluent query-rewriting / clarification call.
//    when(rewriteChatClient.prompt())
//        .thenReturn(rewriteRequestSpec);
//    when(rewriteRequestSpec.system(anyString()))
//        .thenReturn(rewriteRequestSpec);
//    when(rewriteRequestSpec.user(anyString()))
//        .thenReturn(rewriteRequestSpec);
//    when(rewriteRequestSpec.call())
//        .thenReturn(rewriteResponseSpec);
//
//    assistantService = new AssistantService(
//        chatClientBuilder,
//        chatMemory,
//        mock(Resource.class),
//        mock(Resource.class),
//        queryCategoryService,
//        documentRetrievalService
//    );
//  }
//
//  @Test
//  void shouldReturnFallbackWhenNoDocumentsAreRetrieved() {
//    String question = "What is the annual training budget?";
//    String rewrittenQuestion = "annual training budget policy";
//
//    when(queryCategoryService.classify(question))
//        .thenReturn("hr");
//    when(rewriteResponseSpec.content())
//        .thenReturn(rewrittenQuestion);
//    when(documentRetrievalService.retrieve(rewrittenQuestion, "hr"))
//        .thenReturn(List.of());
//
//    String answer = assistantService.askWithRag("alice", question);
//
//    assertThat(answer)
//        .contains("I couldn't find relevant information")
//        .contains("available FinFlow documentation");
//
//    verify(queryCategoryService).classify(question);
//    verify(documentRetrievalService)
//        .retrieve(question, "hr");
//    verify(chatClient, never()).prompt();
//  }
//
//  @Test
//  void shouldGenerateClarificationWithoutRetrievingDocuments() {
//    String question = "How do I get access?";
//    String clarification =
//        "What do you need access to: GitLab, VPN, or something else?";
//
//    when(queryCategoryService.classify(question))
//        .thenReturn("CLARIFY");
//    when(rewriteResponseSpec.content())
//        .thenReturn(clarification);
//
//    String answer = assistantService.askWithRag("alice", question);
//
//    assertThat(answer).isEqualTo(clarification);
//
//    verify(queryCategoryService).classify(question);
//    verify(rewriteChatClient).prompt();
//    verify(documentRetrievalService, never())
//        .retrieve(anyString(), anyString());
//    verify(chatClient, never()).prompt();
//  }
//}