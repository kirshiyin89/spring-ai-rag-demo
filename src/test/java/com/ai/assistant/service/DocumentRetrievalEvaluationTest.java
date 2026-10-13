package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;


/**
 * IT Test. It tests the actual embedding and pgvector search path instead of mocking the vector store.
 */
@SpringBootTest
class DocumentRetrievalEvaluationTest {

  private static final String TEST_DOCUMENT_ID =
      "a1b2c3d4-e5f6-4789-8123-123456789abc";

  @Autowired
  private VectorStore vectorStore;

  @Autowired
  private DocumentRetrievalService retrievalService;


  private static final String GITLAB_ID =
      "a1b2c3d4-e5f6-4789-8123-123456789abc";
  private static final String VPN_ID =
      "b1c2c3d4-e5f6-4789-8123-123456789abc";
  private static final String SOFTWARE_ID =
      "c1c2c3d4-e5f6-4789-8123-123456789abc";

  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("pgvector/pgvector:pg16")
          .withDatabaseName("rag_test")
          .withUsername("test")
          .withPassword("test");

  static {
    postgres.start();
  }

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @BeforeEach
  void setUp() {
    vectorStore.add(List.of(
        Document.builder()
            .id(GITLAB_ID)
            .text("""
                GitLab access:
                Employees must request GitLab access through
                the IT team. The team reviews requests and
                grants permissions according to the employee's role.
                """)
            .metadata(Map.of(
                "filename", "gitlab-access.md",
                "category", "access",
                "department", "IT"))
            .build(),

        Document.builder()
            .id(VPN_ID)
            .text("""
                VPN and WLAN access:
                Employees who need to connect to the company
                network remotely must request VPN access.
                Contact the IT team for VPN setup instructions.
                """)
            .metadata(Map.of(
                "filename", "vpn-and-wlan-access.md",
                "category", "network",
                "department", "IT"))
            .build(),

        Document.builder()
            .id(SOFTWARE_ID)
            .text("""
                Software and license requests:
                Employees must request software installations
                and software licenses through the IT team.
                Employees must not install arbitrary software
                without following the company approval process.
                The IT team reviews software requests.
                """)
            .metadata(Map.of(
                "filename", "software-license-request.md",
                "category", "software",
                "department", "IT"))
            .build()
    ));
  }

  @AfterEach
  void cleanUp() {
    vectorStore.delete(List.of(
        GITLAB_ID,
        VPN_ID,
        SOFTWARE_ID
    ));
  }

  @Test
  void shouldRetrieveExpectedDocumentForGitLabQuestion() {
    List<Document> results = retrievalService.retrieve(
        "Who should I contact to request GitLab access?",
        "access"
    );

    assertThat(results)
        .extracting(document ->
            document.getMetadata().get("filename"))
        .contains("gitlab-access.md");
  }

  @ParameterizedTest
  @CsvSource({
      "'Who should I contact to request GitLab access?', access, gitlab-access.md",
      "'How can I connect to the company VPN?', network, vpn-and-wlan-access.md",
      "'Can I install any software I want?', software, software-license-request.md"
  })
  void shouldRetrieveExpectedDocument(
      String question,
      String category,
      String expectedFilename) {

    List<Document> results =
        retrievalService.retrieve(question, category);

    assertThat(results)
        .extracting(document ->
            document.getMetadata().get("filename"))
        .as("Expected document for question: " + question)
        .contains(expectedFilename);
  }

  @Test
  void shouldNotRetrieveDocumentsForUnsupportedQuestion() {
    List<Document> results = retrievalService.retrieve(
        "What is the annual training budget for each employee?",
        "hr"
    );

    assertThat(results).isEmpty();
  }

}