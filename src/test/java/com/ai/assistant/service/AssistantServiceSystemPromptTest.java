package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.Tenant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;

class AssistantServiceSystemPromptTest {

  @Test
  void failsFastWhenTenantHasNoSystemPrompt() {
    AssistantService service = buildService();
    Tenant tenant = tenant("brewly", null);

    assertThatThrownBy(() -> service.askWithRag("conv-1", "Which coffee is best?", tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("brewly")
        .hasMessageContaining("no system prompt");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\n\t"})
  void rejectsBlankSystemPrompts(String prompt) {
    AssistantService service = buildService();
    Tenant tenant = tenant("finflow", prompt);

    assertThatThrownBy(() -> service.askWithRag("conv-2", "How do I get VPN access?", tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("finflow");
  }

  private static Tenant tenant(String slug, String systemPrompt) {
    Tenant tenant = new Tenant();
    tenant.setId(1L);
    tenant.setSlug(slug);
    tenant.setName(slug);
    tenant.setSystemPrompt(systemPrompt);
    return tenant;
  }

  /**
   * Builds the service with mocks. Only the collaborators reached before the system prompt
   * check are stubbed: empty memory, no ticket route, and a classifier result that is neither
   * NONE nor CLARIFY.
   */
  private static AssistantService buildService() {
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    ChatClient.Builder clone = mock(ChatClient.Builder.class);
    ChatClient client = mock(ChatClient.class);
    when(builder.clone()).thenReturn(clone);
    when(clone.build()).thenReturn(client);
    when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
    when(builder.build()).thenReturn(client);

    ChatMemory chatMemory = mock(ChatMemory.class);
    when(chatMemory.get(anyString())).thenReturn(List.of());

    QueryCategoryService categoryService = mock(QueryCategoryService.class);
    when(categoryService.classify(anyString(), any(Tenant.class))).thenReturn("company");

    return new AssistantService(
        builder,
        chatMemory,
        categoryService,
        null, // compressionQueryTransformer: only used when history exists
        null, // retrievalAugmentationAdvisor: reached after the prompt check
        null, // ticketTools
        mock(TicketSourceRouter.class), // route() returns Optional.empty() by default
        null, // youTrackMcpGateway
        null, // ticketAuthorizationService
        null, // pendingDraftStore
        null, // conversationalIntentClassifier
        null  // tenantService
    );
  }
}
