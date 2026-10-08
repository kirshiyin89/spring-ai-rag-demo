package com.ai.assistant.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VectorStoreService {

  private final VectorStore vectorStore;
  private final TokenTextSplitter textSplitter;

  public VectorStoreService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
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

  public void loadDocuments() throws IOException {

    Resource[] resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath:/onboarding/*.md");

    for (Resource resource : resources) {

      String content = new String(
          resource.getInputStream().readAllBytes(),
          StandardCharsets.UTF_8
      );

      Map<String, Object> metadata = Map.of(
          "filename", Objects.requireNonNull(resource.getFilename()),
          "source", "onboarding",
          "documentType", "internal-documentation"
      );

      String filename = Objects.requireNonNull(resource.getFilename());

      Document document = new Document(
          content,
          createMetadata(filename)
      );
      List<Document> chunks = textSplitter.apply(List.of(document));

      for (Document chunk : chunks) {
        log.info("TEXT:\n{}", chunk.getText());
        log.info("METADATA:\n{}", chunk.getMetadata());
      }

      vectorStore.add(chunks);

      for (int i = 0; i < chunks.size(); i++) {
        log.info(
            "Chunk {} from {}:\n{}",
            i,
            resource.getFilename(),
            chunks.get(i).getText()
        );
      }
    }
  }

}
