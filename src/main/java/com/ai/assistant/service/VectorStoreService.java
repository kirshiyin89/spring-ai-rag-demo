package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
      JdbcTemplate jdbcTemplate
  ) {
    this.vectorStore = vectorStore;
    this.jdbcTemplate = jdbcTemplate;

    this.textSplitter = TokenTextSplitter.builder()
        .withChunkSize(200)
        .build();
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

      String contentHash = ContentHash.sha256(content);
      Long tenantId = tenant.getId();

      // Remove legacy FinFlow chunks that predate tenant metadata.
      if ("finflow".equals(tenant.getSlug())) {
        jdbcTemplate.update(
            """
            DELETE FROM vector_store
            WHERE metadata->>'tenantId' IS NULL
              AND metadata->>'filename' = ?
              AND metadata->>'source' = 'onboarding'
            """,
            filename
        );
      }

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
          getSource(tenant),
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
            getSource(tenant),
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

      metadata.put("source", getSource(tenant));
      metadata.put(
          "documentType",
          "finflow".equals(tenant.getSlug())
              ? "internal-documentation"
              : "business-documentation"
      );
      metadata.put(
          "sourceDocumentId",
          tenant.getSlug() + ":" + filename
      );
      metadata.put("contentHash", contentHash);

      Document document = new Document(content, metadata);

      List<Document> chunks =
          textSplitter.apply(List.of(document));

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

    return switch (tenant.getSlug()) {
      case "finflow" -> "classpath:/onboarding/*.md";
      default -> {
        if (!tenant.getSlug().matches("[a-z0-9-]+")) {
          throw new IllegalArgumentException(
              "Invalid tenant slug: " + tenant.getSlug()
          );
        }

        yield "classpath:/tenants/"
            + tenant.getSlug()
            + "/*.md";
      }
    };
  }

  private String getSource(Tenant tenant) {
    return "finflow".equals(tenant.getSlug())
        ? "onboarding"
        : "knowledge-base";
  }

  private Map<String, Object> createMetadata(
      String filename,
      Tenant tenant
  ) {

    Map<String, Object> metadata = new HashMap<>();

    // Shared metadata: every document must have a tenant ID.
    metadata.put("tenantId", tenant.getId());
    metadata.put("filename", filename);

    if ("finflow".equals(tenant.getSlug())) {

      Map<String, Object> existingMetadata = switch (filename) {
        case "company-overview.md" -> Map.of(
            "category", "company",
            "department", "IT"
        );
        case "new-employee-onboarding.md" -> Map.of(
            "category", "onboarding",
            "department", "IT"
        );
        case "gitlab-access.md" -> Map.of(
            "category", "access",
            "department", "IT"
        );
        case "vpn-and-wlan-access.md" -> Map.of(
            "category", "network",
            "department", "IT"
        );
        case "laptop-request.md", "equipment-request.md" -> Map.of(
            "category", "hardware",
            "department", "IT"
        );
        case "software-license-request.md" -> Map.of(
            "category", "software",
            "department", "IT"
        );
        default -> Map.of(
            "category", "other",
            "department", "IT"
        );
      };

      metadata.putAll(existingMetadata);

    } else {

      String category = switch (filename) {
        case "espresso-guide.md" -> "products";
        case "shipping-policy.md" -> "shipping";
        case "return-policy.md" -> "returns";
        default -> "other";
      };

      metadata.put("category", category);
    }

    return metadata;
  }
}
