package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;

class ConcatenationDocumentJoinerTest {

  @Test
  void shouldCombineResultsAndRemoveDuplicates() {
    Document software = Document.builder()
        .id("software")
        .text("Request software through IT.")
        .build();

    Document approval = Document.builder()
        .id("approval")
        .text("A manager approves the business need.")
        .build();

    Query query1 = Query.builder()
        .text("Who approves paid software?")
        .build();

    Query query2 = Query.builder()
        .text("How do I request a license?")
        .build();

    Map<Query, List<List<Document>>> results = Map.of(
        query1, List.of(List.of(software, approval)),
        query2, List.of(List.of(approval, software))
    );

    ConcatenationDocumentJoiner joiner =
        new ConcatenationDocumentJoiner();

    List<Document> joined = joiner.join(results);

    assertThat(joined)
        .extracting(Document::getId)
        .containsExactlyInAnyOrder("software", "approval");
  }
}