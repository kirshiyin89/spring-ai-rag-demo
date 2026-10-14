package com.ai.assistant.service;

import java.util.List;

import java.util.Locale;
import java.util.Map;
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

  private final VectorStoreDocumentRetriever retriever;

  public List<Document> retrieve(
      String question,
      String category) {

    Query query;

    if (category != null
        && !category.isBlank()
        && !category.equalsIgnoreCase("NONE")) {

      String normalizedCategory =
          category.trim().toLowerCase(Locale.ROOT);

      query = Query.builder()
          .text(question)
          .context(Map.of(
              VectorStoreDocumentRetriever.FILTER_EXPRESSION,
              "category == '" + normalizedCategory + "'"
          ))
          .build();

    } else {
      query = new Query(question);
    }

    List<Document> documents = retriever.retrieve(query);

    log.info(
        "Retrieved {} documents for question: {}",
        documents.size(),
        question
    );

    return documents;
  }
}