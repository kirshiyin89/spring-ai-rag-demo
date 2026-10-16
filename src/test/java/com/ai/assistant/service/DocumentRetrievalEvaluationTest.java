package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;


/**
 * IT Test. It tests the actual embedding and pgvector search path instead of mocking the vector
 * store. Don't forget to add the LLM API KEY to the env vars of this test!
 */
@SpringBootTest
class DocumentRetrievalEvaluationTest {

  @Autowired
  private VectorStore vectorStore;

  @Autowired
  private DocumentRetrievalService retrievalService;

  @Autowired
  private QueryCategoryService queryCategoryService;


  private static final String GITLAB_ID =
      "a1b2c3d4-e5f6-4789-8123-123456789abc";
  private static final String VPN_ID =
      "b1c2c3d4-e5f6-4789-8123-123456789abc";
  private static final String SOFTWARE_ID =
      "c1c2c3d4-e5f6-4789-8123-123456789abc";
  private static final String COMPANY_ID =
      "d1c2c3d4-e5f6-4789-8123-123456789abc";

  private static final String ONBOARDING_ID =
      "e1c2c3d4-e5f6-4789-8123-123456789abc";

  private static final String HARDWARE_ID =
      "f1c2c3d4-e5f6-4789-8123-123456789abc";

  private static final String LICENSE_APPROVAL_ID =
      "22222222-2222-4222-8222-222222222222";

  record RetrievalCase(
      String question,
      String expectedCategory,
      String expectedFilename
  ) {

  }

  static final List<RetrievalCase> RETRIEVAL_CASES = List.of(
      // Direct questions
      new RetrievalCase(
          "Who should I contact to request GitLab access?",
          "access",
          "gitlab-access.md"
      ),
      new RetrievalCase(
          "How can I connect to the company VPN?",
          "network",
          "vpn-and-wlan-access.md"
      ),
      new RetrievalCase(
          "Can I install any software I want?",
          "software",
          "software-license-request.md"
      ),
      new RetrievalCase(
          "What does FinFlow Technologies do?",
          "company",
          "company-overview.md"
      ),
      new RetrievalCase(
          "How do I request a company laptop?",
          "hardware",
          "laptop-request.md"
      ),
      new RetrievalCase(
          "Who approves a paid software license request?",
          "software",
          "license-approval.md"
      ),

      // Paraphrased questions
      new RetrievalCase(
          "I need access to our Git repositories. How do I get it?",
          "access",
          "gitlab-access.md"
      ),
      new RetrievalCase(
          "I work from home. How do I connect to the company network?",
          "network",
          "vpn-and-wlan-access.md"
      ),
      new RetrievalCase(
          "My laptop is too old. How can I get a replacement?",
          "hardware",
          "laptop-request.md"
      ),
      new RetrievalCase(
          "I just joined the company. What should I do first?",
          "onboarding",
          "new-employee-onboarding.md"
      ),
      new RetrievalCase(
          "What information must I include when submitting a software license request?",
          "software",
          "software-license-request.md"
      ),
      // Difficult production-deployment questions
      new RetrievalCase(
          "Can I deploy directly to production?",
          "access",
          "gitlab-access.md"
      ),
      new RetrievalCase(
          "how i get prod deploy access?",
          "access",
          "gitlab-access.md"
      ),
      new RetrievalCase(
          "can i deploy directly to production without testing?",
          "access",
          "gitlab-access.md"
      )
  );


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
                
                GitLab CI/CD can deploy applications to company
                environments. Production deployment permissions
                are restricted.
                
                Developers may have permission to trigger normal
                deployment pipelines without having direct production
                administration access.
                
                Most software developers receive the Developer role
                for their team's projects. Maintainer access requires
                additional justification.
                Production deployment policy:
                
                Employees must not push changes directly to production.
                Production deployments require the appropriate permissions
                and must follow the company's deployment process.
                Having permission to trigger a deployment pipeline does not
                automatically grant permission to deploy directly to production.
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
            .id(COMPANY_ID)
            .text("""
                FinFlow Technologies is a fintech company.
                The company develops financial technology products
                and services for its customers.
                """)
            .metadata(Map.of(
                "filename", "company-overview.md",
                "category", "company",
                "department", "IT"))
            .build(),

        Document.builder()
            .id(ONBOARDING_ID)
            .text("""
                New employee onboarding:
                New employees should complete the onboarding process
                and follow the company onboarding guide.
                Contact the relevant team for help with initial setup.
                """)
            .metadata(Map.of(
                "filename", "new-employee-onboarding.md",
                "category", "onboarding",
                "department", "IT"))
            .build(),

        Document.builder()
            .id(HARDWARE_ID)
            .text("""
                Laptop requests:
                Employees who need a new laptop or a replacement
                should submit a hardware request to the IT team.
                The IT team reviews hardware requests.
                """)
            .metadata(Map.of(
                "filename", "laptop-request.md",
                "category", "hardware",
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
            .build(),

        Document.builder()
            .id(LICENSE_APPROVAL_ID)
            .text("""
                Software license approval:
                
                Employees who request paid software licenses need
                manager approval for the business need. The IT team
                reviews requests before purchase. Procurement handles
                purchasing when required.
                
                Approval does not automatically grant access to the
                software. IT may need to assign the license or install
                the software after approval.
                """)
            .metadata(Map.of(
                "filename", "license-approval.md",
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
        SOFTWARE_ID,
        COMPANY_ID,
        ONBOARDING_ID,
        HARDWARE_ID,
        LICENSE_APPROVAL_ID
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
  @MethodSource("retrievalCases")
  void shouldRetrieveExpectedDocument(RetrievalCase testCase) {
    List<Document> results = retrievalService.retrieve(
        testCase.question(),
        testCase.expectedCategory()
    );

    assertThat(results)
        .extracting(document -> document.getMetadata().get("filename"))
        .as("Expected document for question: " + testCase.question())
        .contains(testCase.expectedFilename());
  }

  static Stream<RetrievalCase> retrievalCases() {
    return RETRIEVAL_CASES.stream();
  }

  @Test
  void shouldReturnNoResultsWhenCategoryHasNoMatchingDocuments() {
    List<Document> results = retrievalService.retrieve(
        "What is the annual training budget for each employee?",
        "hr"
    );

    assertThat(results).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
      "'How do I get access?', CLARIFY",
      "'What is the capital of France?', NONE",
      "'What is the company vacation policy?', hr",
      "'How do I connect to the company VPN?', network",
      "'How do I request GitLab access?', access"
  })
  void shouldClassifyQuestionsCorrectly(
      String question,
      String expectedCategory) {

    assertThat(queryCategoryService.classify(question))
        .as("Category for question: " + question)
        .isEqualToIgnoringCase(expectedCategory);
  }

  @Test
  void shouldFindSoftwareLicenseRequestDetails() {
    List<Document> results = retrievalService.retrieve(
        "What information must I include when requesting paid software?",
        "software"
    );

    results.forEach(document -> {
      System.out.println("Filename: "
          + document.getMetadata().get("filename"));
      System.out.println("Text: " + document.getText());
      System.out.println("---");
    });
  }
}