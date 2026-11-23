package com.ai.assistant.service;

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
 * Crawls the website of every tenant that has a crawl start URL, once a night.
 *
 * <p>If a site is offline, its crawl is retried after a delay (one hour by default), up to a
 * maximum number of attempts. After that, the next nightly run is the only attempt left.
 */
@Slf4j
@Component
public class WebsiteCrawlScheduler {

  private final CrawlIngestionService crawlIngestionService;
  private final TenantService tenantService;
  private final TaskScheduler taskScheduler;
  private final Duration retryDelay;
  private final int maxAttempts;

  public WebsiteCrawlScheduler(
      CrawlIngestionService crawlIngestionService,
      TenantService tenantService,
      TaskScheduler taskScheduler,
      @Value("${app.crawler.retry-delay:1h}") Duration retryDelay,
      @Value("${app.crawler.max-attempts:3}") int maxAttempts) {
    this.crawlIngestionService = crawlIngestionService;
    this.tenantService = tenantService;
    this.taskScheduler = taskScheduler;
    this.retryDelay = retryDelay;
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(cron = "${app.crawler.cron}", zone = "${app.crawler.zone}")
  public void nightlyCrawl() {
    for (Tenant tenant : tenantService.findCrawlableTenants()) {
      runCrawl(tenant, 1);
    }
  }

  /** Runs one crawl attempt for a tenant. Package-private so tests can call it directly. */
  void runCrawl(Tenant tenant, int attempt) {
    try {
      CrawlSummary summary = crawlIngestionService.crawlWebsite(tenant);
      log.info("Scheduled crawl for {} finished on attempt {}: {}",
          tenant.getSlug(), attempt, summary);

    } catch (CrawlAlreadyRunningException exception) {
      // Another crawl holds this tenant's lock. Its result is the current content, so skip.
      log.warn("Skipping scheduled crawl for {} attempt {}: {}",
          tenant.getSlug(), attempt, exception.getMessage());

    } catch (CrawlSiteUnavailableException exception) {
      if (attempt < maxAttempts) {
        Instant retryAt = Instant.now().plus(retryDelay);
        log.warn("Site for {} is unavailable (attempt {} of {}). Retrying at {}: {}",
            tenant.getSlug(), attempt, maxAttempts, retryAt, exception.getMessage());
        taskScheduler.schedule(() -> runCrawl(tenant, attempt + 1), retryAt);
      } else {
        log.error("Site for {} is still unavailable after {} attempts. "
            + "The next nightly run will try again: {}",
            tenant.getSlug(), maxAttempts, exception.getMessage());
      }

    } catch (RuntimeException exception) {
      log.error("Crawl for {} failed unexpectedly", tenant.getSlug(), exception);
    }
  }
}
