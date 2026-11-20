package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.WebPageExtractor.ExtractedPage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class CrawlIngestionServiceTest {

  private static final String START_URL = "http://localhost:3000/";

  private WebCrawlerService crawler;
  private VectorStore vectorStore;
  private JdbcTemplate jdbcTemplate;
  private CrawlIngestionService service;

  private Tenant brewly;
  private Tenant finflow;

  @BeforeEach
  void setUp() {
    crawler = mock(WebCrawlerService.class);
    vectorStore = mock(VectorStore.class);
    jdbcTemplate = mock(JdbcTemplate.class);

    // No stored chunks yet, so every page is new.
    doReturn(List.of()).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any());

    // The crawl lock is free unless a test says otherwise.
    when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true);

    service = new CrawlIngestionService(crawler, vectorStore, jdbcTemplate, START_URL);

    brewly = tenant(2L, "brewly");
    finflow = tenant(1L, "finflow");
  }

  @Test
  void shouldIndexEachCrawledPageUnderTheBrewlyTenant() {
    when(crawler.crawl(START_URL)).thenReturn(List.of(
        new ExtractedPage("http://localhost:3000/products.html", "Products", "products",
            "Espresso beans for home brewing."),
        new ExtractedPage("http://localhost:3000/faq.html", "FAQ", "faq",
            "Shipping takes two to three business days.")));

    service.crawlBrewly(brewly);

    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, org.mockito.Mockito.times(2)).add(captor.capture());

    List<Document> first = captor.getAllValues().get(0);
    assertEquals(2L, first.getFirst().getMetadata().get("tenantId"));
    assertEquals("web-crawl", first.getFirst().getMetadata().get("source"));
    assertEquals("http://localhost:3000/products.html",
        first.getFirst().getMetadata().get("sourceUrl"));
    assertEquals("/products.html", first.getFirst().getMetadata().get("filename"));
    assertEquals("products", first.getFirst().getMetadata().get("category"));
  }

  @Test
  void shouldRejectACrawlWhileAnotherCrawlHoldsTheLock() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(false);

    assertThrows(CrawlAlreadyRunningException.class, () -> service.crawlBrewly(brewly));

    verifyNoInteractions(crawler, vectorStore);
  }

  @Test
  void shouldRejectCrawlForAnyTenantOtherThanBrewly() {
    assertThrows(IllegalArgumentException.class, () -> service.crawlBrewly(finflow));

    verifyNoInteractions(crawler, vectorStore);
  }

  private static Tenant tenant(Long id, String slug) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setSlug(slug);
    tenant.setName(slug);
    return tenant;
  }
}
