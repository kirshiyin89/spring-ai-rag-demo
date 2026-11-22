package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PublicChatRateLimiterTest {

  private static final long WINDOW_MILLIS = 60_000L;

  @Test
  void shouldAllowRequestsUpToLimitThenBlock() {
    var clock = new AtomicLong(0L);
    var limiter = new PublicChatRateLimiter(3, WINDOW_MILLIS, clock::get);

    assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
    assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
    assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
    assertThat(limiter.tryAcquire("203.0.113.5")).isFalse();
  }

  @Test
  void shouldResetAfterWindowElapses() {
    var clock = new AtomicLong(0L);
    var limiter = new PublicChatRateLimiter(1, WINDOW_MILLIS, clock::get);

    assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
    assertThat(limiter.tryAcquire("203.0.113.5")).isFalse();

    clock.set(WINDOW_MILLIS);

    assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
  }

  @Test
  void shouldTrackClientsIndependently() {
    var clock = new AtomicLong(0L);
    var limiter = new PublicChatRateLimiter(1, WINDOW_MILLIS, clock::get);

    assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
    assertThat(limiter.tryAcquire("203.0.113.5")).isFalse();
    assertThat(limiter.tryAcquire("198.51.100.7")).isTrue();
  }
}
