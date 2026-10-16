package com.ai.assistant.config;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;


@Configuration
public class AiConfig {

  @Bean
  ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
  }

  @Value("classpath:/prompts/user-prompt.st")
  private Resource userPromptResource;

  @Value("${app.rag.similarity-threshold}")
  private double similarityThreshold;


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

  @Bean
  VectorStoreDocumentRetriever vectorStoreDocumentRetriever(
      VectorStore vectorStore) {

    return VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .topK(10)
        .similarityThreshold(similarityThreshold)
        .build();
  }


  @Bean
  RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
      RewriteQueryTransformer rewriteQueryTransformer,
      VectorStoreDocumentRetriever retriever) {

    PromptTemplate promptTemplate =
        new PromptTemplate(userPromptResource);

    ContextualQueryAugmenter queryAugmenter =
        ContextualQueryAugmenter.builder()
            .allowEmptyContext(false)
            .promptTemplate(promptTemplate)
            .build();

    return RetrievalAugmentationAdvisor.builder()
        .queryTransformers(rewriteQueryTransformer)
        .documentRetriever(retriever)
        .queryAugmenter(queryAugmenter)
        .build();
  }


}