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
  private final MarkdownChunker markdownChunker;

  public VectorStoreService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
    this.markdownChunker = new MarkdownChunker();
  }

  public void loadDocuments() throws IOException {

    Resource[] resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath:/onboarding/*.md");

    for (Resource resource : resources) {

      String content = new String(
          resource.getInputStream().readAllBytes(),
          StandardCharsets.UTF_8
      );

      List<String> sections = markdownChunker.split(content);

      List<Document> chunks = sections.stream()
          .map(section -> new Document(
              section,
              Map.of("filename", Objects.requireNonNull(resource.getFilename()))
          ))
          .toList();

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
