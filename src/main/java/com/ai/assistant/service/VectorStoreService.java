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

      List<Document> chunks = textSplitter.apply(List.of(document));

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
