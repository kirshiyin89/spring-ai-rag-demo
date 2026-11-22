package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryCategoryService {

  private final ChatClient chatClient;

  /**
   * Classifies a question with the tenant's own classifier prompt. The prompt and its
   * categories live in tenants.classifier_prompt, so a new client needs no Java change.
   */
  public String classify(String question, Tenant tenant) {
    String classifierPrompt = tenant.requireSetting(
        tenant.getClassifierPrompt(), "classifier prompt"
    );

    return Objects.requireNonNull(
        chatClient.prompt()
            .system(classifierPrompt)
            .user(question)
            .call()
            .content()
    ).trim();
  }
}
