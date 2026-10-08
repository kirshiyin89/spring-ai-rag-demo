package com.ai.assistant.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryCategoryService {

  private final ChatClient chatClient;

  public String classify(String question) {

    return Objects.requireNonNull(chatClient.prompt()
            .system("""
                Classify the user's question into exactly one of these categories:
                
                company
                onboarding
                access
                hardware
                software
                network
                support
                hr
                
                If the question does not belong to any of these categories,
                return NONE.
                
                Return only the category name.
                Do not return explanations.
                """)
            .user(question)
            .call()
            .content())
        .trim();
  }
}