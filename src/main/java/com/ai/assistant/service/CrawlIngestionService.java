package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.WebPageExtractor.ExtractedPage;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crawls the Brewly website and stores each page in the vector store under the Brewly tenant.
 *
 * <p>Pages are matched by URL within the tenant. Unchanged pages are skipped, changed pages
 * replace their old chunks, and chunks for pages that no longer exist on the site are removed.
 */
@Slf4j
@Service
public class CrawlIngestionService {

  static final String SOURCE = "web-crawl";
  public static final String BREWLY_SLUG = "brewly";
  private static final String CRAWL_LOCK_NAME = "brewly-website-crawl";

  private final WebCrawlerService crawler;
  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;
  private final TokenTextSplitter textSplitter;
  private final String brewlyStartUrl;

  public CrawlIngestionService(
      WebCrawlerService crawler,
      VectorStore vectorStore,
      JdbcTemplate jdbcTemplate,
      @Value("${app.crawler.brewly.start-url}") String brewlyStartUrl) {
    this.crawler = crawler;
    this.vectorStore = vectorStore;
    this.jdbcTemplate = jdbcTemplate;
    this.brewlyStartUrl = brewlyStartUrl;
    this.textSplitter = TokenTextSplitter.builder()
        .withChunkSize(200)
        .build();
  }

  /**
   * Crawls the configured Brewly website and updates the vector store.
   * The start URL comes from configuration, never from a request, and only the Brewly tenant is accepted.
   */
  @Transactional(rollbackFor = Exception.class)
  public CrawlSummary crawlBrewly(Tenant tenant) {

    if (!BREWLY_SLUG.equals(tenant.getSlug())) {
      throw new IllegalArgumentException(
          "Website crawling is only allowed for the brewly tenant");
    }

    // Held until this transaction ends, so only one crawl runs at a time, across all
    // callers and application instances.
    if (!acquireCrawlLock()) {
      throw new CrawlAlreadyRunningException();
    }

    List<ExtractedPage> pages = crawler.crawl(brewlyStartUrl);

    if (pages.isEmpty()) {
      // Keep the stored content. An empty result usually means the site is down.
      log.warn("Crawl of {} found no pages; stored content was not changed", brewlyStartUrl);
      return new CrawlSummary(0, 0, 0, 0);
    }

    int indexed = 0;
    int updated = 0;
    int skipped = 0;
    Set<String> crawledUrls = new HashSet<>();

    for (ExtractedPage page : pages) {

      crawledUrls.add(page.url());

      String contentHash = ContentHash.sha256(page.title() + "\n" + page.text());

      List<String> storedHashes = jdbcTemplate.query(
          """
          SELECT DISTINCT COALESCE(metadata->>'contentHash', '')
          FROM vector_store
          WHERE metadata->>'tenantId' = ?
            AND metadata->>'source' = ?
            AND metadata->>'sourceUrl' = ?
          """,
          (rs, rowNum) -> rs.getString(1),
          tenant.getId().toString(),
          SOURCE,
          page.url()
      );

      if (storedHashes.size() == 1 && storedHashes.getFirst().equals(contentHash)) {
        skipped++;
        continue;
      }

      if (!storedHashes.isEmpty()) {
        deleteChunks(tenant, page.url());
        updated++;
      }

      Document document = new Document(
          page.title() + "\n\n" + page.text(),
          createMetadata(page, tenant, contentHash)
      );

      vectorStore.add(textSplitter.apply(List.of(document)));
      indexed++;
    }

    int removed = removeStalePages(tenant, crawledUrls);

    CrawlSummary summary = new CrawlSummary(indexed, updated, skipped, removed);
    log.info("Brewly crawl finished: {}", summary);
    return summary;
  }

  /**
   * Takes a transaction-scoped Postgres advisory lock. It returns false when another
   * transaction already holds it, and the lock is released when the transaction ends.
   */
  private boolean acquireCrawlLock() {
    Boolean acquired = jdbcTemplate.queryForObject(
        "SELECT pg_try_advisory_xact_lock(hashtext(?))",
        Boolean.class,
        CRAWL_LOCK_NAME
    );
    return Boolean.TRUE.equals(acquired);
  }

  private int removeStalePages(Tenant tenant, Set<String> crawledUrls) {

    List<String> storedUrls = jdbcTemplate.query(
        """
        SELECT DISTINCT metadata->>'sourceUrl'
        FROM vector_store
        WHERE metadata->>'tenantId' = ?
          AND metadata->>'source' = ?
        """,
        (rs, rowNum) -> rs.getString(1),
        tenant.getId().toString(),
        SOURCE
    );

    int removed = 0;
    for (String url : storedUrls) {
      if (url != null && !crawledUrls.contains(url)) {
        deleteChunks(tenant, url);
        log.info("Removed chunks for page no longer on the site: {}", url);
        removed++;
      }
    }
    return removed;
  }

  private void deleteChunks(Tenant tenant, String sourceUrl) {
    jdbcTemplate.update(
        """
        DELETE FROM vector_store
        WHERE metadata->>'tenantId' = ?
          AND metadata->>'source' = ?
          AND metadata->>'sourceUrl' = ?
        """,
        tenant.getId().toString(),
        SOURCE,
        sourceUrl
    );
  }

  private Map<String, Object> createMetadata(
      ExtractedPage page,
      Tenant tenant,
      String contentHash) {

    Map<String, Object> metadata = new HashMap<>();

    // Every document must carry the tenant ID, or retrieval will not find it.
    metadata.put("tenantId", tenant.getId());
    metadata.put("filename", pathOf(page.url()));
    metadata.put("source", SOURCE);
    metadata.put("documentType", "business-documentation");
    metadata.put("sourceDocumentId", tenant.getSlug() + ":" + page.url());
    metadata.put("sourceUrl", page.url());
    metadata.put("title", Objects.requireNonNullElse(page.title(), ""));
    metadata.put("category", Objects.requireNonNullElse(page.category(), "other"));
    metadata.put("contentHash", contentHash);
    return metadata;
  }

  /** The path part of a URL, for example {@code /products.html}. Used as the filename metadata. */
  private static String pathOf(String url) {
    String path = URI.create(url).getPath();
    return path == null || path.isEmpty() ? "/" : path;
  }

  /** Counts of what one crawl did. */
  public record CrawlSummary(int indexed, int updated, int skipped, int removed) {
  }
}
