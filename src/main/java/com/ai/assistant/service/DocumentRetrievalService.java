
package com.ai.assistant.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRetrievalService {

  private static final Set<String> ALLOWED_CATEGORIES = Set.of(
      "company",
      "onboarding",
      "access",
      "hardware",
      "software",
      "network",
      "support",
      "hr"
  );

  private final VectorStoreDocumentRetriever retriever;

  public List<Document> retrieve(
      String question,
      String category) {

    if (category == null || category.isBlank()) {
      log.warn("Skipping retrieval: category is missing");
      return List.of();
    }

    String normalizedCategory =
        category.trim().toLowerCase(Locale.ROOT);

    if (!ALLOWED_CATEGORIES.contains(normalizedCategory)) {
      log.warn(
          "Skipping retrieval: unsupported category '{}'",
          normalizedCategory
      );
      return List.of();
    }

    Query query = Query.builder()
        .text(question)
        .context(Map.of(
            VectorStoreDocumentRetriever.FILTER_EXPRESSION,
            "category == '" + normalizedCategory + "'"
        ))
        .build();

    List<Document> documents = retriever.retrieve(query);

    log.info(
        "Retrieved {} documents for category '{}'",
        documents.size(),
        normalizedCategory
    );

    return documents;
  }
}