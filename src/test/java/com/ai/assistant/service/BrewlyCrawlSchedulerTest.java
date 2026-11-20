package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class BrewlyCrawlSchedulerTest {

  private CrawlIngestionService crawlIngestionService;
  private TenantService tenantService;
  private TaskScheduler taskScheduler;
  private Tenant brewly;

  @BeforeEach
  void setUp() {
    crawlIngestionService = mock(CrawlIngestionService.class);
    tenantService = mock(TenantService.class);
    taskScheduler = mock(TaskScheduler.class);

    brewly = new Tenant();
    brewly.setId(2L);
    brewly.setSlug("brewly");
    when(tenantService.resolveBySlug("brewly")).thenReturn(brewly);
  }

  private BrewlyCrawlScheduler schedulerWithMaxAttempts(int maxAttempts) {
    return new BrewlyCrawlScheduler(crawlIngestionService, tenantService, taskScheduler,
        Duration.ofHours(1), maxAttempts);
  }

  @Test
  void shouldNotRetryWhenTheCrawlSucceeds() {
    when(crawlIngestionService.crawlBrewly(brewly))
        .thenReturn(new CrawlSummary(6, 0, 0, 0));

    schedulerWithMaxAttempts(3).nightlyCrawl();

    verify(crawlIngestionService).crawlBrewly(brewly);
    verifyNoInteractions(taskScheduler);
  }

  @Test
  void shouldRetryOneHourLaterWhenTheSiteIsOffline() {
    when(crawlIngestionService.crawlBrewly(brewly))
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
    when(crawlIngestionService.crawlBrewly(brewly))
        .thenThrow(new CrawlSiteUnavailableException("offline"));

    BrewlyCrawlScheduler scheduler = schedulerWithMaxAttempts(2);
    scheduler.nightlyCrawl();

    // The first failure schedules one retry. Run that retry; it fails again and is the last attempt.
    ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).schedule(retry.capture(), any(Instant.class));
    retry.getValue().run();

    verify(crawlIngestionService, times(2)).crawlBrewly(brewly);
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void shouldNotRetryOnUnexpectedErrors() {
    when(crawlIngestionService.crawlBrewly(brewly))
        .thenThrow(new IllegalStateException("database down"));

    schedulerWithMaxAttempts(3).nightlyCrawl();

    verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
  }
}
