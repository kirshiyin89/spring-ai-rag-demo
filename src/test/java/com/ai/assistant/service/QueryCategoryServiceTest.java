package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

class QueryCategoryServiceTest {

  @Test
  void usesTheTenantsOwnClassifierPrompt() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.user(anyString())).thenReturn(spec);
    when(spec.call()).thenReturn(call);
    when(call.content()).thenReturn(" COFFEE ");

    Tenant tenant = tenant("acme", "Classify into ACME_TOPIC or NONE.");

    String category = new QueryCategoryService(chatClient).classify("Q?", tenant);

    assertThat(category).isEqualTo("COFFEE");
    verify(spec).system(eq("Classify into ACME_TOPIC or NONE."));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void failsFastWhenClassifierPromptIsMissing(String prompt) {
    ChatClient chatClient = mock(ChatClient.class);
    Tenant tenant = tenant("acme", prompt);

    assertThatThrownBy(() -> new QueryCategoryService(chatClient).classify("Q?", tenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme")
        .hasMessageContaining("classifier prompt");
  }

  private static Tenant tenant(String slug, String classifierPrompt) {
    Tenant tenant = new Tenant();
    tenant.setId(7L);
    tenant.setSlug(slug);
    tenant.setName(slug);
    tenant.setClassifierPrompt(classifierPrompt);
    return tenant;
  }
}
