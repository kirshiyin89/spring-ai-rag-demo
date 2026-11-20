package com.ai.assistant.service;

import static com.ai.assistant.service.CrawlIngestionService.BREWLY_SLUG;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.CrawlIngestionService.CrawlSummary;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the Brewly website crawl every night.
 *
 * <p>If the site is offline, the crawl is retried after a delay (one hour by default), up to
 * a maximum number of attempts. After that, the next nightly run is the only attempt left.
 */
@Slf4j
@Component
public class BrewlyCrawlScheduler {

  private final CrawlIngestionService crawlIngestionService;
  private final TenantService tenantService;
  private final TaskScheduler taskScheduler;
  private final Duration retryDelay;
  private final int maxAttempts;

  public BrewlyCrawlScheduler(
      CrawlIngestionService crawlIngestionService,
      TenantService tenantService,
      TaskScheduler taskScheduler,
      @Value("${app.crawler.brewly.retry-delay:1h}") Duration retryDelay,
      @Value("${app.crawler.brewly.max-attempts:3}") int maxAttempts) {
    this.crawlIngestionService = crawlIngestionService;
    this.tenantService = tenantService;
    this.taskScheduler = taskScheduler;
    this.retryDelay = retryDelay;
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(cron = "${app.crawler.brewly.cron}", zone = "${app.crawler.brewly.zone}")
  public void nightlyCrawl() {
    runCrawl(1);
  }

  /** Runs one crawl attempt. Package-private so tests can call it directly. */
  void runCrawl(int attempt) {
    try {
      Tenant brewly = tenantService.resolveBySlug(BREWLY_SLUG);
      CrawlSummary summary = crawlIngestionService.crawlBrewly(brewly);
      log.info("Scheduled Brewly crawl finished on attempt {}: {}", attempt, summary);

    } catch (CrawlAlreadyRunningException exception) {
      // Another crawl holds the lock. Its result is the current content, so skip this run.
      log.warn("Skipping scheduled Brewly crawl attempt {}: {}", attempt, exception.getMessage());

    } catch (CrawlSiteUnavailableException exception) {
      if (attempt < maxAttempts) {
        Instant retryAt = Instant.now().plus(retryDelay);
        log.warn("Brewly site is unavailable (attempt {} of {}). Retrying at {}: {}",
            attempt, maxAttempts, retryAt, exception.getMessage());
        taskScheduler.schedule(() -> runCrawl(attempt + 1), retryAt);
      } else {
        log.error("Brewly site is still unavailable after {} attempts. "
            + "The next nightly run will try again: {}", maxAttempts, exception.getMessage());
      }

    } catch (RuntimeException exception) {
      log.error("Brewly crawl failed unexpectedly", exception);
    }
  }
}
