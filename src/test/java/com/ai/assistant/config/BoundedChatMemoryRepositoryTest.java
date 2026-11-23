package com.ai.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

class BoundedChatMemoryRepositoryTest {

  private static final Duration TWO_HOURS = Duration.ofHours(2);

  private final FakeTicker ticker = new FakeTicker();

  @Test
  void keepsNoMoreConversationsThanTheLimit() {
    BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(2, TWO_HOURS, ticker);

    repository.saveAll("a", List.of(new UserMessage("first")));
    repository.saveAll("b", List.of(new UserMessage("second")));
    repository.saveAll("c", List.of(new UserMessage("third")));

    // Caffeine chooses which conversation to drop, so only the count and the newest are fixed.
    assertThat(repository.findConversationIds()).hasSize(2).contains("c");
  }

  @Test
  void forgetsAConversationThatHasBeenIdleTooLong() {
    BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(10, TWO_HOURS, ticker);

    repository.saveAll("quiet", List.of(new UserMessage("hello")));
    ticker.advance(Duration.ofMinutes(90));
    repository.saveAll("busy", List.of(new UserMessage("hi")));
    ticker.advance(Duration.ofMinutes(90)); // "quiet" is now 180 minutes idle, "busy" 90

    assertThat(repository.findConversationIds()).containsExactly("busy");
    assertThat(repository.findByConversationId("quiet")).isEmpty();
  }

  @Test
  void activityResetsTheIdleClock() {
    BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(10, TWO_HOURS, ticker);

    repository.saveAll("chat", List.of(new UserMessage("hello")));
    ticker.advance(Duration.ofMinutes(90));
    repository.findByConversationId("chat"); // read counts as activity, so the clock restarts
    ticker.advance(Duration.ofMinutes(90));

    assertThat(repository.findByConversationId("chat")).hasSize(1);
  }

  @Test
  void deleteRemovesOnlyTheNamedConversation() {
    BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(10, TWO_HOURS, ticker);

    repository.saveAll("a", List.of(new UserMessage("one")));
    repository.saveAll("b", List.of(new UserMessage("two")));
    repository.deleteByConversationId("a");

    assertThat(repository.findConversationIds()).containsExactly("b");
  }

  @Test
  void rejectsLimitsThatCannotWork() {
    assertThatThrownBy(() -> new BoundedChatMemoryRepository(0, TWO_HOURS, ticker))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BoundedChatMemoryRepository(5, Duration.ZERO, ticker))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A ticker the test can move forward, so idle timeouts can be checked without waiting. */
  private static final class FakeTicker implements Ticker {
    private long nanos;

    void advance(Duration duration) {
      nanos += duration.toNanos();
    }

    @Override
    public long read() {
      return nanos;
    }
  }
}
