package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.CrawlIngestionService.CrawlSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class WebsiteCrawlSchedulerTest {

  private CrawlIngestionService crawlIngestionService;
  private TenantService tenantService;
  private TaskScheduler taskScheduler;
  private Tenant brewly;

  @BeforeEach
  void setUp() {
    crawlIngestionService = mock(CrawlIngestionService.class);
    tenantService = mock(TenantService.class);
    taskScheduler = mock(TaskScheduler.class);

    brewly = tenant(2L, "brewly");
    when(tenantService.findCrawlableTenants()).thenReturn(List.of(brewly));
  }

  private WebsiteCrawlScheduler schedulerWithMaxAttempts(int maxAttempts) {
    return new WebsiteCrawlScheduler(crawlIngestionService, tenantService, taskScheduler,
        Duration.ofHours(1), maxAttempts);
  }

  @Test
  void shouldNotRetryWhenTheCrawlSucceeds() {
    when(crawlIngestionService.crawlWebsite(brewly))
        .thenReturn(new CrawlSummary(6, 0, 0, 0));

    schedulerWithMaxAttempts(3).nightlyCrawl();

    verify(crawlIngestionService).crawlWebsite(brewly);
    verifyNoInteractions(taskScheduler);
  }

  @Test
  void shouldCrawlEveryTenantThatHasAWebsite() {
    Tenant second = tenant(3L, "second-shop");
    when(tenantService.findCrawlableTenants()).thenReturn(List.of(brewly, second));
    when(crawlIngestionService.crawlWebsite(any(Tenant.class)))
        .thenReturn(new CrawlSummary(0, 0, 0, 0));

    schedulerWithMaxAttempts(3).nightlyCrawl();

    verify(crawlIngestionService).crawlWebsite(brewly);
    verify(crawlIngestionService).crawlWebsite(second);
  }

  @Test
  void shouldRetryOneHourLaterWhenTheSiteIsOffline() {
    when(crawlIngestionService.crawlWebsite(brewly))
        .thenThrow(new CrawlSiteUnavailableException("offline"));

    Instant before = Instant.now();
    schedulerWithMaxAttempts(3).nightlyCrawl();
    Instant after = Instant.now();

    ArgumentCaptor<Instant> retryAt = ArgumentCaptor.forClass(Instant.class);
    verify(taskScheduler).schedule(any(Runnable.class), retryAt.capture());
    assertFalse(retryAt.getValue().isBefore(before.plus(Duration.ofHours(1))));
    assertFalse(retryAt.getValue().isAfter(after.plus(Duration.ofHours(1))));
  }

  @Test
  void shouldStopRetryingAfterTheMaximumNumberOfAttempts() {
    when(crawlIngestionService.crawlWebsite(brewly))
        .thenThrow(new CrawlSiteUnavailableException("offline"));

    WebsiteCrawlScheduler scheduler = schedulerWithMaxAttempts(2);
    scheduler.nightlyCrawl();

    // The first failure schedules one retry. Run that retry; it fails again and is the last attempt.
    ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).schedule(retry.capture(), any(Instant.class));
    retry.getValue().run();

    verify(crawlIngestionService, times(2)).crawlWebsite(brewly);
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void shouldNotRetryOnUnexpectedErrors() {
    when(crawlIngestionService.crawlWebsite(brewly))
        .thenThrow(new IllegalStateException("database down"));

    schedulerWithMaxAttempts(3).nightlyCrawl();

    verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
  }

  private static Tenant tenant(Long id, String slug) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setSlug(slug);
    tenant.setName(slug);
    return tenant;
  }
}
