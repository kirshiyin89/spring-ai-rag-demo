
package com.ai.assistant.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class VectorStoreService {

  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;
  private final TokenTextSplitter textSplitter;

  public VectorStoreService(
      VectorStore vectorStore,
      JdbcTemplate jdbcTemplate) {

    this.vectorStore = vectorStore;
    this.jdbcTemplate = jdbcTemplate;

    this.textSplitter = TokenTextSplitter.builder()
        .withChunkSize(200)
        .build();
  }

  private Map<String, Object> createMetadata(String filename) {

    return switch (filename) {
      case "company-overview.md" -> Map.of(
          "filename", filename,
          "category", "company",
          "department", "IT"
      );
      case "new-employee-onboarding.md" -> Map.of(
          "filename", filename,
          "category", "onboarding",
          "department", "IT"
      );
      case "gitlab-access.md" -> Map.of(
          "filename", filename,
          "category", "access",
          "department", "IT"
      );
      case "vpn-and-wlan-access.md" -> Map.of(
          "filename", filename,
          "category", "network",
          "department", "IT"
      );
      case "laptop-request.md", "equipment-request.md" -> Map.of(
          "filename", filename,
          "category", "hardware",
          "department", "IT"
      );
      case "software-license-request.md" -> Map.of(
          "filename", filename,
          "category", "software",
          "department", "IT"
      );
      default -> Map.of(
          "filename", filename,
          "category", "other",
          "department", "IT"
      );
    };
  }

  @Transactional(rollbackFor = Exception.class)
  public void loadDocuments() throws IOException {

    Resource[] resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath:/onboarding/*.md");

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

      String contentHash = calculateHash(content);

      List<String> storedHashes = jdbcTemplate.query(
          """
              SELECT DISTINCT COALESCE(metadata->>'contentHash', '')
              FROM vector_store
              WHERE metadata->>'filename' = ?
              """,
          (rs, rowNum) -> rs.getString(1),
          filename
      );

      // The document is already indexed with the same content.
      if (storedHashes.size() == 1
          && storedHashes.getFirst().equals(contentHash)) {

        log.info("Skipping unchanged document: {}", filename);
        skipped++;
        continue;
      }

      // Remove old chunks before indexing new or changed content.
      if (!storedHashes.isEmpty()) {
        jdbcTemplate.update(
            """
                DELETE FROM vector_store
                WHERE metadata->>'filename' = ?
                """,
            filename
        );

        log.info("Removed old chunks for: {}", filename);
        updated++;
      }

      Map<String, Object> metadata =
          new HashMap<>(createMetadata(filename));

      metadata.put("source", "onboarding");
      metadata.put("documentType", "internal-documentation");
      metadata.put("sourceDocumentId", filename);
      metadata.put("contentHash", contentHash);

      Document document = new Document(content, metadata);

      List<Document> chunks =
          textSplitter.apply(List.of(document));

      vectorStore.add(chunks);

      log.info(
          "Indexed {} chunks for {}",
          chunks.size(),
          filename
      );

      indexed++;
    }

    log.info(
        "Document ingestion finished. Indexed: {}, updated: {}, skipped: {}",
        indexed,
        updated,
        skipped
    );
  }

  private String calculateHash(String content) {

    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(hash);

    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
          "SHA-256 algorithm is unavailable",
          exception
      );
    }
  }
}
