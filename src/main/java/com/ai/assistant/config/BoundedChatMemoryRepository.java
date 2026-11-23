package com.ai.assistant.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

/**
 * In-memory chat history with two limits: a cap on how many conversations are kept, and an
 * idle timeout after which a conversation is forgotten.
 *
 * <p>The per-conversation message window is applied by {@code MessageWindowChatMemory}; this
 * class limits the number of conversations, which anonymous callers could otherwise grow
 * without bound.
 *
 * <p>Caffeine does the bookkeeping. Reads and writes both count as activity, so a conversation
 * that is still being used never reaches its idle timeout. When the cap is reached, Caffeine
 * decides which conversation to drop based on how often and how recently it was used.
 */
public class BoundedChatMemoryRepository implements ChatMemoryRepository {

  private final Cache<String, List<Message>> conversations;

  public BoundedChatMemoryRepository(int maxConversations, Duration idleTimeout, Ticker ticker) {
    if (maxConversations < 1) {
      throw new IllegalArgumentException("maxConversations must be at least 1");
    }
    if (idleTimeout.isNegative() || idleTimeout.isZero()) {
      throw new IllegalArgumentException("idleTimeout must be positive");
    }
    this.conversations = Caffeine.newBuilder()
        .maximumSize(maxConversations)
        .expireAfterAccess(idleTimeout)
        .ticker(ticker)
        .build();
  }

  @Override
  public List<String> findConversationIds() {
    // Expired and evicted entries are removed lazily; clean up so the listing is accurate.
    conversations.cleanUp();
    return List.copyOf(conversations.asMap().keySet());
  }

  @Override
  public List<Message> findByConversationId(String conversationId) {
    List<Message> messages = conversations.getIfPresent(conversationId);
    return messages == null ? List.of() : List.copyOf(messages);
  }

  @Override
  public void saveAll(String conversationId, List<Message> messages) {
    conversations.put(conversationId, List.copyOf(messages));
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    conversations.invalidate(conversationId);
  }
}
