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
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crawls a tenant's website and stores each page in the vector store under that tenant.
 *
 * <p>Pages are matched by URL within the tenant. Unchanged pages are skipped, changed pages
 * replace their old chunks, and chunks for pages that no longer exist on the site are removed.
 */
@Slf4j
@Service
public class CrawlIngestionService {

  static final String SOURCE = "web-crawl";

  private final WebCrawlerService crawler;
  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;

  public CrawlIngestionService(
      WebCrawlerService crawler,
      VectorStore vectorStore,
      JdbcTemplate jdbcTemplate) {
    this.crawler = crawler;
    this.vectorStore = vectorStore;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Crawls the tenant's configured website and updates the vector store. The start URL comes
   * from the tenant's row in the database, never from a request.
   */
  @Transactional(rollbackFor = Exception.class)
  public CrawlSummary crawlWebsite(Tenant tenant) {

    String startUrl = tenant.getCrawlStartUrl();
    if (startUrl == null || startUrl.isBlank()) {
      throw new IllegalArgumentException(
          "Website crawling is not configured for tenant " + tenant.getSlug());
    }

    // Held until this transaction ends, so only one crawl per tenant runs at a time, across
    // all callers and application instances.
    if (!acquireCrawlLock(tenant)) {
      throw new CrawlAlreadyRunningException();
    }

    List<ExtractedPage> pages = crawler.crawl(startUrl);

    if (pages.isEmpty()) {
      // Keep the stored content. An empty result usually means the site is down.
      log.warn("Crawl of {} found no pages; stored content was not changed", startUrl);
      return new CrawlSummary(0, 0, 0, 0);
    }

    int indexed = 0;
    int updated = 0;
    int skipped = 0;
    Set<String> crawledUrls = new HashSet<>();

    for (ExtractedPage page : pages) {

      crawledUrls.add(page.url());

      // The chunking version is part of the hash, so a change to the chunking rules
      // re-indexes pages whose text did not change.
      String contentHash = ContentHash.sha256(
          SectionChunker.VERSION + "\n" + page.title() + "\n" + page.text());

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

      Map<String, Object> metadata = createMetadata(page, tenant, contentHash);
      List<Document> chunks = SectionChunker.chunk(page.title(), sectionsOf(page)).stream()
          .map(text -> new Document(text, new HashMap<>(metadata)))
          .toList();

      vectorStore.add(chunks);
      indexed++;
    }

    int removed = removeStalePages(tenant, crawledUrls);

    CrawlSummary summary = new CrawlSummary(indexed, updated, skipped, removed);
    log.info("Crawl finished for tenant {}: {}", tenant.getSlug(), summary);
    return summary;
  }

  /**
   * Takes a transaction-scoped Postgres advisory lock for this tenant. It returns false when
   * another transaction already holds it, and the lock is released when the transaction ends.
   */
  private boolean acquireCrawlLock(Tenant tenant) {
    Boolean acquired = jdbcTemplate.queryForObject(
        "SELECT pg_try_advisory_xact_lock(hashtext(?))",
        Boolean.class,
        "website-crawl:" + tenant.getSlug()
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

  /** Pages without sections (for example from an older extractor) are chunked as one section. */
  private static List<SectionChunker.Section> sectionsOf(ExtractedPage page) {
    if (page.sections().isEmpty()) {
      return List.of(new SectionChunker.Section("", page.text()));
    }
    return page.sections();
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
