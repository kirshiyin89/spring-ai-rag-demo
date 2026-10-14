package com.ai.assistant.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRetrievalService {

  @Value("${app.rag.similarity-threshold:0.35}")
  private double similarityThreshold;

  private final VectorStore vectorStore;

  public List<Document> retrieve(String question, String category) {
    return retrieve(question, category, similarityThreshold);
  }

  public List<Document> retrieve(
      String question,
      String category,
      double threshold) {

    SearchRequest.Builder searchRequest = SearchRequest.builder()
        .query(question)
        .topK(10)
        .similarityThreshold(threshold);

    if (category != null
        && !category.isBlank()
        && !category.equals("NONE")) {
      searchRequest.filterExpression("category == '" + category + "'");
    }

    List<Document> documents =
        vectorStore.similaritySearch(searchRequest.build());

    log.info("Retrieved {} documents for question: {}", documents.size(), question);
    return documents;
  }
}