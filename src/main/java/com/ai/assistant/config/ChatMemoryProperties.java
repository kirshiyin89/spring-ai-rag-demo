package com.ai.assistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits for chat history kept in memory.
 *
 * @param maxMessages       messages kept per conversation (the sliding window sent to the model)
 * @param maxConversations  conversations kept at once; the least recently used is dropped first
 * @param idleTimeout       a conversation untouched for this long is forgotten
 */
@ConfigurationProperties(prefix = "app.chat.memory")
public record ChatMemoryProperties(
    @DefaultValue("20") int maxMessages,
    @DefaultValue("1000") int maxConversations,
    @DefaultValue("2h") Duration idleTimeout
) {
}
