package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.assistant.persistence.PendingIssueDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Import(PendingIssueDraftStore.class)
@Testcontainers
class PendingIssueDraftStoreIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
      .withDatabaseName("drafts")
      .withUsername("drafts")
      .withPassword("drafts");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private PendingIssueDraftStore store;

  @Autowired
  private PendingIssueDraftRepository repository;

  @BeforeEach
  void cleanDatabase() {
    repository.deleteAll();
  }

  @Test
  void draftSurvivesAStoreReconstructionAndCompletesItsLifecycle() {
    var collecting = store.startCollecting("alice", "conversation-1", "Laptop access fails.");
    var pending = store.save("alice", "conversation-1",
        new AssistantResponse.IssueDraft("Cannot access laptop", "Laptop access fails after login."));

    var restartedStore = new PendingIssueDraftStore(repository);
    var restored = restartedStore.get("alice", "conversation-1");

    assertTrue(restored.isPresent());
    assertEquals(collecting.draftId(), restored.orElseThrow().draftId());
    assertEquals(PendingIssueDraftStore.Status.PENDING, restored.orElseThrow().status());
    assertEquals("Cannot access laptop", restored.orElseThrow().draft().summary());

    var claimed = restartedStore.claim("alice", "conversation-1", pending.draftId());

    assertTrue(claimed.isPresent());
    assertEquals(PendingIssueDraftStore.Status.CREATING, claimed.orElseThrow().status());
    assertTrue(restartedStore.complete("alice", "conversation-1", pending.draftId()));
    assertTrue(restartedStore.get("alice", "conversation-1").isEmpty());
  }
}
