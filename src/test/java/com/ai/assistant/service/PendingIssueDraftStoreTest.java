package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.PendingIssueDraftEntity;
import com.ai.assistant.persistence.PendingIssueDraftRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingIssueDraftStoreTest {

  private final Map<String, PendingIssueDraftEntity> drafts = new HashMap<>();
  private PendingIssueDraftStore store;

  @BeforeEach
  void setUp() {
    drafts.clear();
    PendingIssueDraftRepository repository = mock(PendingIssueDraftRepository.class);

    when(repository.findByUsernameAndConversationId(anyString(), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(drafts.get(key(
            invocation.getArgument(0), invocation.getArgument(1)))));
    when(repository.save(any(PendingIssueDraftEntity.class)))
        .thenAnswer(invocation -> {
          PendingIssueDraftEntity entity = invocation.getArgument(0);
          drafts.put(key(entity.getUsername(), entity.getConversationId()), entity);
          return entity;
        });
    when(repository.claimDraft(any(UUID.class), anyString(), anyString(),
        eq(PendingIssueDraftStore.Status.PENDING),
        eq(PendingIssueDraftStore.Status.CREATING)))
        .thenAnswer(invocation -> {
          UUID draftId = invocation.getArgument(0);
          PendingIssueDraftEntity entity = drafts.get(key(
              invocation.getArgument(1), invocation.getArgument(2)));

          if (entity == null || !draftId.equals(entity.getDraftId())
              || entity.getStatus() != PendingIssueDraftStore.Status.PENDING) {
            return 0;
          }

          entity.setStatus(PendingIssueDraftStore.Status.CREATING);
          return 1;
        });
    when(repository.deleteByDraftIdAndUsernameAndConversationIdAndStatus(
        any(UUID.class), anyString(), anyString(), any(PendingIssueDraftStore.Status.class)))
        .thenAnswer(invocation -> {
          UUID draftId = invocation.getArgument(0);
          String username = invocation.getArgument(1);
          String conversationId = invocation.getArgument(2);
          PendingIssueDraftStore.Status status = invocation.getArgument(3);
          PendingIssueDraftEntity entity = drafts.get(key(username, conversationId));

          if (entity == null || !draftId.equals(entity.getDraftId())
              || entity.getStatus() != status) {
            return 0;
          }

          drafts.remove(key(username, conversationId));
          return 1;
        });

    store = new PendingIssueDraftStore(repository);
  }

  @Test
  void startCollectingPersistsContextAndSavePromotesTheSameDraft() {
    var collecting = store.startCollecting("alice", "conversation-1", "My laptop is locked.");
    var saved = store.save("alice", "conversation-1",
        new AssistantResponse.IssueDraft("Laptop access issue", "User cannot access laptop."));

    assertEquals(PendingIssueDraftStore.Status.COLLECTING, collecting.status());
    assertTrue(collecting.draft().description().contains("My laptop is locked."));
    assertEquals(collecting.draftId(), saved.draftId());
    assertEquals(collecting.createdAt(), saved.createdAt());
    assertEquals(PendingIssueDraftStore.Status.PENDING, saved.status());
    assertEquals("Laptop access issue", saved.draft().summary());
  }

  @Test
  void claimAndCompleteRemoveOnlyTheClaimedPendingDraft() {
    var draft = store.save("alice", "conversation-1",
        new AssistantResponse.IssueDraft("VPN problem", "VPN disconnects."));

    var claimed = store.claim("alice", "conversation-1", draft.draftId());

    assertTrue(claimed.isPresent());
    assertEquals(PendingIssueDraftStore.Status.CREATING, claimed.get().status());
    assertTrue(store.complete("alice", "conversation-1", draft.draftId()));
    assertTrue(store.get("alice", "conversation-1").isEmpty());
  }

  @Test
  void cannotClaimOrDeleteWithTheWrongDraftId() {
    var draft = store.save("alice", "conversation-1",
        new AssistantResponse.IssueDraft("VPN problem", "VPN disconnects."));

    assertTrue(store.claim("alice", "conversation-1", UUID.randomUUID().toString()).isEmpty());
    assertFalse(store.cancel("alice", "conversation-1", UUID.randomUUID().toString()));
    assertTrue(store.get("alice", "conversation-1").isPresent());
    assertEquals(PendingIssueDraftStore.Status.PENDING,
        store.get("alice", "conversation-1").orElseThrow().status());
    assertTrue(store.cancel("alice", "conversation-1", draft.draftId()));
  }

  private String key(String username, String conversationId) {
    return username + ':' + conversationId;
  }
}
