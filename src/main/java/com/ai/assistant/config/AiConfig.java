package com.ai.assistant.config;

import com.ai.assistant.service.KeywordOverlapDocumentPostProcessor;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;


@Configuration
@Slf4j
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
  MultiQueryExpander multiQueryExpander(
      ChatClient.Builder builder) {

    return MultiQueryExpander.builder()
        .chatClientBuilder(builder.clone())
        .numberOfQueries(3)
        .includeOriginal(true)
        .build();
  }

  @Bean
  QueryExpander loggingQueryExpander(
      MultiQueryExpander delegate) {

    return query -> {
      List<Query> expandedQueries = delegate.expand(query);

      log.info("Query expansion input: {}", query.text());

      for (int i = 0; i < expandedQueries.size(); i++) {
        log.info(
            "Query variant [{}]: {}",
            i + 1,
            expandedQueries.get(i).text());
      }

      return expandedQueries;
    };
  }

  @Bean
  RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
      @Qualifier("loggingQueryExpander") QueryExpander queryExpander,
      VectorStoreDocumentRetriever retriever,
      KeywordOverlapDocumentPostProcessor postProcessor) {

    PromptTemplate promptTemplate =
        new PromptTemplate(userPromptResource);

    ContextualQueryAugmenter queryAugmenter =
        ContextualQueryAugmenter.builder()
            .allowEmptyContext(true)
            .promptTemplate(promptTemplate)
            .build();

    return RetrievalAugmentationAdvisor.builder()
        .queryExpander(queryExpander)
        .documentRetriever(retriever)
        .documentPostProcessors(postProcessor)
        .queryAugmenter(queryAugmenter)
        .build();
  }

  @Bean
  ChatClient toolCallingChatClient(ChatClient.Builder builder) {
    return builder.clone().build();
  }

}