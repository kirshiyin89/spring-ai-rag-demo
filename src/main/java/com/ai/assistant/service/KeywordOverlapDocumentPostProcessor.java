
package com.ai.assistant.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KeywordOverlapDocumentPostProcessor
    implements DocumentPostProcessor {

  @Override
  public @NonNull List<Document> process(
      Query query,
      List<Document> documents) {

    Set<String> queryTerms = tokenize(query.text());

    log.info("=== BEFORE RERANKING ===");

    for (Document document : documents) {
      log.info(
          "filename={}, keywordScore={}",
          document.getMetadata().get("filename"),
          overlapScore(queryTerms, document.getText()));
    }

    List<Document> reranked = documents.stream()
        .sorted(
            Comparator.comparingInt(
                    (Document document) ->
                        overlapScore(queryTerms, document.getText()))
                .reversed())
        .toList();

    log.info("Post-processing query: {}", query.text());

    for (int i = 0; i < reranked.size(); i++) {
      Document document = reranked.get(i);

      log.info(
          "Post-processed rank {}: filename={}, keywordScore={}",
          i + 1,
          document.getMetadata().get("filename"),
          overlapScore(queryTerms, document.getText()));
    }

    return reranked;
  }

  private int overlapScore(
      Set<String> queryTerms,
      String documentText) {

    Set<String> documentTerms = tokenize(documentText);

    return (int) queryTerms.stream()
        .filter(documentTerms::contains)
        .count();
  }

  private Set<String> tokenize(String text) {
    return Arrays.stream(
            text.toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+"))
        .filter(token -> token.length() > 2)
        .collect(Collectors.toSet());
  }
}
