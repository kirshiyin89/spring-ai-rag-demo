package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.Tenant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Checks that the per-tenant messages and prompts come from the tenant, never from
 * Java code shared with other tenants.
 */
class AssistantServiceTenantSettingsTest {

  @Test
  void returnsTheTenantsOwnOutOfScopeMessage() {
    Tenant tenant = tenant("acme", "Acme system prompt.");
    tenant.setOutOfScopeMessage("Acme can only help with widgets.");

    String answer = buildService("NONE", null)
        .askWithRag("conv-1", "Who won the match?", tenant);

    assertThat(answer).isEqualTo("Acme can only help with widgets.");
  }

  @Test
  void failsFastWhenOutOfScopeMessageIsMissing() {
    Tenant tenant = tenant("acme", "Acme system prompt.");

    assertThatThrownBy(() -> buildService("NONE", null)
        .askWithRag("conv-2", "Who won the match?", tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme")
        .hasMessageContaining("out-of-scope message");
  }

  @Test
  void clarifiesWithTheTenantsOwnClarificationPrompt() {
    ChatClient client = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
    when(client.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.user(anyString())).thenReturn(spec);
    when(spec.call()).thenReturn(call);
    when(call.content()).thenReturn("Which widget do you mean?");

    Tenant tenant = tenant("acme", "Acme system prompt.");
    tenant.setClarificationPrompt("Ask one question about the widget.");

    String answer = buildService("CLARIFY", client)
        .askWithRag("conv-3", "Tell me about it", tenant);

    assertThat(answer).isEqualTo("Which widget do you mean?");
    verify(spec).system(eq("Ask one question about the widget."));
  }

  @Test
  void failsFastWhenClarificationPromptIsMissing() {
    Tenant tenant = tenant("acme", "Acme system prompt.");

    assertThatThrownBy(() -> buildService("CLARIFY", null)
        .askWithRag("conv-4", "Tell me about it", tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("clarification prompt");
  }

  private static Tenant tenant(String slug, String systemPrompt) {
    Tenant tenant = new Tenant();
    tenant.setId(9L);
    tenant.setSlug(slug);
    tenant.setName(slug);
    tenant.setSystemPrompt(systemPrompt);
    return tenant;
  }

  /**
   * Builds the service with a fixed classifier result. The shared client mock is used
   * for both the main and clarification clients when a test needs model output.
   */
  private static AssistantService buildService(String category, ChatClient modelClient) {
    ChatClient client = modelClient != null ? modelClient : mock(ChatClient.class);

    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    ChatClient.Builder clone = mock(ChatClient.Builder.class);
    when(builder.clone()).thenReturn(clone);
    when(clone.build()).thenReturn(client);
    when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
    when(builder.build()).thenReturn(client);

    ChatMemory chatMemory = mock(ChatMemory.class);
    when(chatMemory.get(anyString())).thenReturn(List.of());

    QueryCategoryService categoryService = mock(QueryCategoryService.class);
    when(categoryService.classify(anyString(), any(Tenant.class))).thenReturn(category);

    return new AssistantService(
        builder,
        chatMemory,
        categoryService,
        null,
        null,
        null,
        mock(TicketSourceRouter.class),
        null,
        null,
        null,
        null,
        null
    );
  }
}
