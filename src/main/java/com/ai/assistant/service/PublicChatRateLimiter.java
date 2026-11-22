package com.ai.assistant.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed-window, per-key request limiter for the anonymous public chat endpoint.
 *
 * <p>State is held in memory, so limits apply per application instance.
 */
@Component
public class PublicChatRateLimiter {

  private static final long ONE_MINUTE_MILLIS = 60_000L;
  private static final int SWEEP_THRESHOLD = 10_000;

  private final int maxRequestsPerWindow;
  private final long windowMillis;
  private final LongSupplier clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Autowired
  public PublicChatRateLimiter(
      @Value("${app.public-chat.max-requests-per-minute:10}") int maxRequestsPerMinute) {
    this(maxRequestsPerMinute, ONE_MINUTE_MILLIS, System::currentTimeMillis);
  }

  PublicChatRateLimiter(int maxRequestsPerWindow, long windowMillis, LongSupplier clock) {
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.windowMillis = windowMillis;
    this.clock = clock;
  }

  /**
   * Records a request for the key and reports whether it is within the limit.
   *
   * @return {@code true} if the request is allowed, {@code false} if the key is over its limit
   */
  public boolean tryAcquire(String key) {
    long now = clock.getAsLong();

    if (windows.size() > SWEEP_THRESHOLD) {
      windows.values().removeIf(window -> now - window.startMillis() >= windowMillis);
    }

    Window updated = windows.compute(key, (ignored, current) -> {
      if (current == null || now - current.startMillis() >= windowMillis) {
        return new Window(now, 1);
      }
      return new Window(current.startMillis(), current.count() + 1);
    });

    return updated.count() <= maxRequestsPerWindow;
  }

  private record Window(long startMillis, int count) {
  }
}
