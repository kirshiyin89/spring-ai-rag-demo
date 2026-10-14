package com.ai.assistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

  @Bean
  ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
  }

  @Bean
  RewriteQueryTransformer rewriteQueryTransformer(
      ChatClient.Builder builder) {

    return RewriteQueryTransformer.builder()
        .chatClientBuilder(builder.clone())
        .targetSearchSystem("""
            An internal company documentation vector store
            containing FinFlow onboarding, IT access, hardware,
            software, network, HR, and deployment procedures.
            Rewrite the input as a clear, standalone search query.
            Preserve the user's intent and important details.
            Do not answer the question.
            """)
        .build();
  }

  @Bean
  CompressionQueryTransformer compressionQueryTransformer(
      ChatClient.Builder builder) {

    return CompressionQueryTransformer.builder()
        .chatClientBuilder(builder.clone())
        .build();
  }
}