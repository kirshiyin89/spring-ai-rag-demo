package com.ai.assistant.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VectorStoreService {

  private final VectorStore vectorStore;

  public VectorStoreService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void loadDocuments() throws IOException {

    Resource[] resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath:/onboarding/*.md");

    for (Resource resource : resources) {

      String content = new String(
          resource.getInputStream().readAllBytes(),
          StandardCharsets.UTF_8
      );

      Document document = new Document(
          content,
          Map.of("filename", Objects.requireNonNull(resource.getFilename()))
      );

      vectorStore.add(List.of(document));

      log.info("Loaded: {}", resource.getFilename());
    }
  }

}
