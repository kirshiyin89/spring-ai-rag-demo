package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.mockito.Mockito.mock;

class DocumentRetrievalServiceTest {

  private VectorStore vectorStore;
  private DocumentRetrievalService retrievalService;

  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    retrievalService = new DocumentRetrievalService(vectorStore);
  }

  @Test
  void shouldReturnMatchingGitLabDocument() {
    Document document = Document.builder()
        .text("Contact the IT team to request GitLab access.")
        .metadata(java.util.Map.of(
            "filename", "gitlab-access.md",
            "category", "access"
        ))
        .build();

    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(document));

    List<Document> results = retrievalService.retrieve(
        "Who should I contact to get access to GitLab?",
        "access"
    );

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getMetadata())
        .containsEntry("filename", "gitlab-access.md");

    verify(vectorStore).similaritySearch(any(SearchRequest.class));
  }

  @Test
  void shouldApplySimilarityThresholdAndCategoryFilter() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of());

    retrievalService.retrieve(
        "Can I deploy directly to production?",
        "access"
    );

    ArgumentCaptor<SearchRequest> captor =
        ArgumentCaptor.forClass(SearchRequest.class);

    verify(vectorStore).similaritySearch(captor.capture());

    SearchRequest request = captor.getValue();

    assertThat(request.getSimilarityThreshold()).isEqualTo(0.35);
  }

  @Test
  void shouldReturnEmptyListWhenNoDocumentsMatch() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of());

    List<Document> results = retrievalService.retrieve(
        "What is the annual training budget?",
        "hr"
    );

    assertThat(results).isEmpty();
  }
}