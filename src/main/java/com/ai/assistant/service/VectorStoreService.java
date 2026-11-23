package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a tenant's Markdown knowledge files into the vector store.
 *
 * <p>Each tenant's files live in {@code classpath:/tenants/<slug>/}. The "source",
 * "documentType" and per-file metadata come from the tenant's row, so a new tenant needs
 * no change here.
 */
@Slf4j
@Service
public class VectorStoreService {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String DEFAULT_METADATA_KEY = "*";

  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;

  public VectorStoreService(
      VectorStore vectorStore,
      JdbcTemplate jdbcTemplate
  ) {
    this.vectorStore = vectorStore;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(rollbackFor = Exception.class)
  public void loadDocuments(Tenant tenant) throws IOException {

    String resourcePattern = getResourcePattern(tenant);

    Resource[] resources = new PathMatchingResourcePatternResolver()
        .getResources(resourcePattern);

    int indexed = 0;
    int skipped = 0;
    int updated = 0;

    for (Resource resource : resources) {

      String filename = Objects.requireNonNull(resource.getFilename());

      String content;
      try (InputStream inputStream = resource.getInputStream()) {
        content = new String(
            inputStream.readAllBytes(),
            StandardCharsets.UTF_8
        );
      }

      // The chunking version is part of the hash, so a change to the chunking rules
      // re-indexes files whose content did not change.
      String contentHash = ContentHash.sha256(SectionChunker.VERSION + "\n" + content);
      Long tenantId = tenant.getId();

      // Check the content hash within the current tenant and source only.
      List<String> storedHashes = jdbcTemplate.query(
          """
          SELECT DISTINCT COALESCE(metadata->>'contentHash', '')
          FROM vector_store
          WHERE metadata->>'tenantId' = ?
            AND metadata->>'source' = ?
            AND metadata->>'filename' = ?
          """,
          (rs, rowNum) -> rs.getString(1),
          tenantId.toString(),
          tenant.getKnowledgeSource(),
          filename
      );

      // Skip unchanged documents.
      if (storedHashes.size() == 1
          && storedHashes.getFirst().equals(contentHash)) {

        log.info(
            "Skipping unchanged document for tenant {}: {}",
            tenant.getSlug(),
            filename
        );

        skipped++;
        continue;
      }

      // Remove previous chunks belonging to this tenant only.
      if (!storedHashes.isEmpty()) {

        jdbcTemplate.update(
            """
            DELETE FROM vector_store
            WHERE metadata->>'tenantId' = ?
              AND metadata->>'source' = ?
              AND metadata->>'filename' = ?
            """,
            tenantId.toString(),
            tenant.getKnowledgeSource(),
            filename
        );

        log.info(
            "Removed old chunks for tenant {}: {}",
            tenant.getSlug(),
            filename
        );

        updated++;
      }

      Map<String, Object> metadata =
          createMetadata(filename, tenant);

      metadata.put("source", tenant.getKnowledgeSource());
      metadata.put("documentType", tenant.getKnowledgeDocumentType());
      metadata.put(
          "sourceDocumentId",
          tenant.getSlug() + ":" + filename
      );
      metadata.put("contentHash", contentHash);

      List<SectionChunker.Section> sections = SectionChunker.fromMarkdown(content);
      String title = SectionChunker.titleOf(sections, filename);

      List<Document> chunks = SectionChunker.chunk(title, sections).stream()
          .map(text -> new Document(text, new HashMap<>(metadata)))
          .toList();

      vectorStore.add(chunks);

      log.info(
          "Indexed {} chunks for tenant {} from {}",
          chunks.size(),
          tenant.getSlug(),
          filename
      );

      indexed++;
    }

    log.info(
        """
        Document ingestion finished for tenant {}.
        Indexed: {}, updated: {}, skipped: {}
        """,
        tenant.getSlug(),
        indexed,
        updated,
        skipped
    );
  }

  private String getResourcePattern(Tenant tenant) {

    if (!tenant.getSlug().matches("[a-z0-9-]+")) {
      throw new IllegalArgumentException(
          "Invalid tenant slug: " + tenant.getSlug()
      );
    }

    return "classpath:/tenants/" + tenant.getSlug() + "/*.md";
  }

  /**
   * Builds the per-document metadata from the tenant's {@code document_metadata} JSON.
   * A document without its own entry gets the entry under the "*" key.
   */
  private Map<String, Object> createMetadata(
      String filename,
      Tenant tenant
  ) {

    Map<String, Object> metadata = new HashMap<>();

    // Shared metadata: every document must have a tenant ID.
    metadata.put("tenantId", tenant.getId());
    metadata.put("filename", filename);

    Map<String, Map<String, String>> byFilename = parseDocumentMetadata(tenant);
    Map<String, String> attributes = byFilename.containsKey(filename)
        ? byFilename.get(filename)
        : byFilename.getOrDefault(DEFAULT_METADATA_KEY, Map.of());

    metadata.putAll(attributes);

    return metadata;
  }

  private Map<String, Map<String, String>> parseDocumentMetadata(Tenant tenant) {
    String json = tenant.getDocumentMetadata();
    if (json == null || json.isBlank()) {
      return Map.of();
    }

    try {
      return JSON.readValue(json, new TypeReference<Map<String, Map<String, String>>>() {
      });
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Tenant " + tenant.getSlug() + " has invalid document_metadata JSON", exception);
    }
  }
}
