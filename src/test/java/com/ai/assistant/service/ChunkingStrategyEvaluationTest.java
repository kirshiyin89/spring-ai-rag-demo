package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Measurement, not a regression test. It compares chunking strategies on the real Brewly
 * website and the real FinFlow documents, using the production retrieval settings (top-K 10,
 * similarity threshold 0.35) and the local embedding model. It calls no LLM and writes nothing
 * to Postgres. The results are printed as a table.
 *
 * <p>Simplification: each question is searched as typed. The production pipeline also expands
 * the question into several variants first, so these numbers are a lower bound for each
 * strategy, not an exact replay of a chat answer.
 */
class ChunkingStrategyEvaluationTest {

  private static final double THRESHOLD = 0.35;
  private static final int TOP_K = 10;
  private static final int HIT_RANK = 5;

  // Section strategy settings, the starting values from the plan.
  private static final int MIN_CHUNK_CHARS = 250;
  private static final int MAX_CHUNK_CHARS = 800;

  private static final Path SITE_DIR = Path.of("../brewly-website");
  private static final Path FINFLOW_DIR = Path.of("src/main/resources/tenants/finflow");

  /**
   * A question with the file it should come from and a phrase that the answer chunk must
   * contain. The phrase is checked against the corpus before any strategy is scored.
   */
  record Question(String tenant, String text, String file, String mustContain) {
  }

  static final List<Question> QUESTIONS = List.of(
      new Question("brewly", "What are your opening hours?", "/contact.html", "Monday to Friday"),
      new Question("brewly", "What is your phone number?", "/contact.html", "+36 1 555 0123"),
      new Question("brewly", "What is your email address?", "/contact.html", "support@brewly-coffee.example"),
      new Question("brewly", "Where is your postal address?", "/contact.html", "Kávé utca 12"),
      new Question("brewly", "How long does delivery take?", "/shipping.html", "2–3 business days"),
      new Question("brewly", "Do you offer free delivery?", "/shipping.html", "15,000 HUF"),
      new Question("brewly", "Where do you roast your coffee?", "/shipping.html", "roasted or packed in Budapest"),
      new Question("brewly", "How many days do I have to return an unopened package?", "/returns.html", "14 days"),
      new Question("brewly", "How long does a refund take?", "/returns.html", "5 business days"),
      new Question("brewly", "Which coffee is sweeter, House Blend or Budapest Night?", "/faq.html", "smoother and sweeter"),
      new Question("brewly", "Which coffee has a soft, round sweetness?", "/products.html", "soft, round sweetness"),
      new Question("brewly", "How much coffee do I grind for espresso?", "/brewing-guide.html", "18 grams"),
      new Question("brewly", "My espresso tastes sour and runs fast, what should I change?", "/brewing-guide.html", "grind finer"),
      new Question("brewly", "Should I grind the beans just before brewing?", "/faq.html", "grind the beans just before brewing"),
      new Question("finflow", "Who should I contact to request GitLab access?", "gitlab-access.md", "manager or team lead requests GitLab access"),
      new Question("finflow", "How do I connect to the company VPN?", "vpn-and-wlan-access.md", "Open the company VPN client"),
      new Question("finflow", "What do I need to access the VPN?", "vpn-and-wlan-access.md", "VPN access requires"),
      new Question("finflow", "Which network should personal devices use?", "vpn-and-wlan-access.md", "guest network")
  );

  /** One document before chunking. Sections are used by the section strategy only. */
  record Doc(String tenant, String filename, String title, String flatText, List<Section> sections) {
  }

  record Section(String heading, String body) {
  }

  @Test
  void compareChunkingStrategies() throws Exception {
    List<Doc> brewly = brewlyDocs();
    List<Doc> finflow = finflowDocs();
    List<Doc> all = new ArrayList<>();
    all.addAll(brewly);
    all.addAll(finflow);

    verifyQuestionsAreAnswerable(all);

    EmbeddingModel embeddings = newEmbeddingModel();

    System.out.println();
    System.out.println("Chunking evaluation: top-K " + TOP_K + ", threshold " + THRESHOLD
        + ", hit = answer chunk in top " + HIT_RANK);
    System.out.printf("%-14s %-8s %6s %6s %8s %8s%n",
        "strategy", "tenant", "chunks", "hit@5", "MRR", "questions");

    for (Strategy strategy : Strategy.values()) {
      for (String tenant : List.of("brewly", "finflow")) {
        List<Doc> docs = all.stream().filter(d -> d.tenant().equals(tenant)).toList();
        List<Document> chunks = chunk(docs, strategy);

        VectorStore store = SimpleVectorStore.builder(embeddings).build();
        store.add(chunks);

        int hits = 0;
        double reciprocalRankSum = 0;
        int count = 0;
        for (Question question : QUESTIONS) {
          if (!question.tenant().equals(tenant)) {
            continue;
          }
          count++;
          int rank = rankOfAnswer(store, question);
          if (rank > 0 && rank <= HIT_RANK) {
            hits++;
          }
          if (rank > 0) {
            reciprocalRankSum += 1.0 / rank;
          }
          System.out.printf("   %-14s %-8s rank=%s  %s%n",
              strategy, tenant, rank > 0 ? rank : "miss", question.text());
          if (rank == 0) {
            System.out.println("      diagnostic: " + unthresholdedRank(store, question, chunks.size()));
          }
        }

        System.out.printf("%-14s %-8s %6d %5d/%d %8.2f %8d%n",
            strategy, tenant, chunks.size(), hits, count,
            count == 0 ? 0 : reciprocalRankSum / count, count);
      }
    }
  }

  /** Questions the Brewly and FinFlow content does not answer. Used to measure noise. */
  static final List<String> OFF_TOPIC = List.of(
      "What is the capital of France?",
      "Can you write me a poem about the sea?",
      "How do I fix a printer that will not print?"
  );

  /**
   * Varies only the similarity threshold, for each strategy. Reports hit@5 and MRR for the
   * questions, and how many chunks the off-topic questions still get back (the noise cost).
   */
  @Test
  void compareThresholds() throws Exception {
    List<Doc> all = new ArrayList<>();
    all.addAll(brewlyDocs());
    all.addAll(finflowDocs());
    EmbeddingModel embeddings = newEmbeddingModel();
    double[] thresholds = {0.35, 0.25, 0.15};

    System.out.println();
    System.out.println("THRESHOLDS: hit@5 / MRR per tenant, and average chunks returned for off-topic questions");
    System.out.printf("%-14s %-8s %7s %8s %8s %14s%n",
        "strategy", "tenant", "thresh", "hit@5", "MRR", "offTopicChunks");

    for (Strategy strategy : Strategy.values()) {
      for (String tenant : List.of("brewly", "finflow")) {
        List<Doc> docs = all.stream().filter(d -> d.tenant().equals(tenant)).toList();
        VectorStore store = SimpleVectorStore.builder(embeddings).build();
        store.add(chunk(docs, strategy));

        for (double threshold : thresholds) {
          int hits = 0;
          int count = 0;
          double reciprocalRankSum = 0;
          for (Question question : QUESTIONS) {
            if (!question.tenant().equals(tenant)) {
              continue;
            }
            count++;
            int rank = rankOfAnswer(store, question, threshold);
            if (rank > 0 && rank <= HIT_RANK) {
              hits++;
            }
            if (rank > 0) {
              reciprocalRankSum += 1.0 / rank;
            }
          }

          int offTopic = 0;
          for (String text : OFF_TOPIC) {
            offTopic += store.similaritySearch(SearchRequest.builder()
                .query(text)
                .topK(TOP_K)
                .similarityThreshold(threshold)
                .build()).size();
          }

          System.out.printf("%-14s %-8s %7.2f %5d/%d %8.2f %14.1f%n",
              strategy, tenant, threshold, hits, count,
              count == 0 ? 0 : reciprocalRankSum / count,
              offTopic / (double) OFF_TOPIC.size());
        }
      }
    }
  }

  /** The phrase must exist in the corpus, otherwise a miss would mean nothing. */
  private static void verifyQuestionsAreAnswerable(List<Doc> docs) {
    for (Question question : QUESTIONS) {
      boolean found = docs.stream()
          .filter(d -> d.tenant().equals(question.tenant()))
          .filter(d -> matchesFile(d.filename(), question.file()))
          .anyMatch(d -> d.flatText().contains(question.mustContain()));
      assertThat(found)
          .as("answer phrase for '%s' is in %s", question.text(), question.file())
          .isTrue();
    }
  }

  private static int rankOfAnswer(VectorStore store, Question question) {
    return rankOfAnswer(store, question, THRESHOLD);
  }

  private static int rankOfAnswer(VectorStore store, Question question, double threshold) {
    List<Document> results = store.similaritySearch(SearchRequest.builder()
        .query(question.text())
        .topK(TOP_K)
        .similarityThreshold(threshold)
        .build());

    for (int i = 0; i < results.size(); i++) {
      Document result = results.get(i);
      boolean sameFile = matchesFile(String.valueOf(result.getMetadata().get("filename")),
          question.file());
      if (sameFile && result.getText().contains(question.mustContain())) {
        return i + 1;
      }
    }
    return 0;
  }

  /**
   * For a miss: where the answer chunk ranks with no similarity threshold, and its score.
   * This separates "the chunk is too far from the question" (low score) from "the threshold
   * cut it off" (good score, but missing from the threshold-filtered results).
   */
  private static String unthresholdedRank(VectorStore store, Question question, int chunkCount) {
    List<Document> all = store.similaritySearch(SearchRequest.builder()
        .query(question.text())
        .topK(Math.max(chunkCount, 1))
        .similarityThreshold(0.0)
        .build());

    for (int i = 0; i < all.size(); i++) {
      Document result = all.get(i);
      if (matchesFile(String.valueOf(result.getMetadata().get("filename")), question.file())
          && result.getText().contains(question.mustContain())) {
        return String.format("rank=%d score=%.3f (threshold %.2f)", i + 1, result.getScore(), THRESHOLD);
      }
    }
    return "answer chunk not in store";
  }

  private static boolean matchesFile(String filename, String expected) {
    return filename.equals(expected);
  }

  enum Strategy {
    FLAT_100, FLAT_200, FLAT_400, SECTIONS
  }

  private static List<Document> chunk(List<Doc> docs, Strategy strategy) {
    List<Document> chunks = new ArrayList<>();
    for (Doc doc : docs) {
      List<String> texts = switch (strategy) {
        case FLAT_100 -> flat(doc, 100);
        case FLAT_200 -> flat(doc, 200);
        case FLAT_400 -> flat(doc, 400);
        case SECTIONS -> sectionChunks(doc);
      };
      for (String text : texts) {
        chunks.add(new Document(text, Map.of("filename", doc.filename())));
      }
    }
    return chunks;
  }

  /** The current behaviour: token-based splitting of the flat document text. */
  private static List<String> flat(Doc doc, int chunkSize) {
    Document whole = new Document(doc.flatText(), Map.of("filename", doc.filename()));
    return TokenTextSplitter.builder()
        .withChunkSize(chunkSize)
        .build()
        .apply(List.of(whole))
        .stream()
        .map(Document::getText)
        .toList();
  }

  /**
   * Section strategy: merge small neighbouring sections until a chunk reaches MIN_CHUNK_CHARS,
   * split any chunk longer than MAX_CHUNK_CHARS, and prefix every chunk with "page › heading".
   */
  static List<String> sectionChunks(Doc doc) {
    List<String> merged = new ArrayList<>();
    StringBuilder pending = null;
    String pendingHeading = "";

    for (Section section : doc.sections()) {
      String rendered = section.heading().isEmpty()
          ? section.body()
          : section.heading() + "\n" + section.body();

      if (pending == null) {
        pending = new StringBuilder();
        pendingHeading = section.heading();
      } else {
        pending.append('\n');
      }
      pending.append(rendered);

      if (pending.length() >= MIN_CHUNK_CHARS) {
        merged.add(prefixed(doc, pendingHeading, pending.toString()));
        pending = null;
      }
    }
    if (pending != null) {
      merged.add(prefixed(doc, pendingHeading, pending.toString()));
    }

    List<String> chunks = new ArrayList<>();
    for (String chunk : merged) {
      if (chunk.length() <= MAX_CHUNK_CHARS) {
        chunks.add(chunk);
        continue;
      }
      // Split an oversize chunk by tokens; each piece keeps the page and heading prefix.
      String heading = chunk.substring(0, chunk.indexOf('\n'));
      String body = chunk.substring(chunk.indexOf('\n') + 1);
      TokenTextSplitter.builder().withChunkSize(200).build()
          .apply(List.of(new Document(body, Map.of())))
          .forEach(piece -> chunks.add(heading + "\n" + piece.getText()));
    }
    return chunks;
  }

  private static String prefixed(Doc doc, String heading, String text) {
    String label = heading.isEmpty() ? doc.title() : doc.title() + " › " + heading;
    return label + "\n" + text;
  }

  // ---- Corpus ----------------------------------------------------------------------------

  private static List<Doc> brewlyDocs() throws IOException {
    WebPageExtractor extractor = new WebPageExtractor();
    List<Doc> docs = new ArrayList<>();
    try (Stream<Path> files = Files.list(SITE_DIR)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".html")).sorted().toList()) {
        String name = "/" + file.getFileName();
        String html = Files.readString(file, StandardCharsets.UTF_8);
        WebPageExtractor.ExtractedPage page = extractor.extract("http://localhost:3000" + name, html);
        docs.add(new Doc("brewly", name, page.title(),
            page.title() + "\n\n" + page.text(), htmlSections(html, page.title())));
      }
    }
    // The file-based Brewly documents are indexed too, through the markdown path.
    Path tenantDir = Path.of("src/main/resources/tenants/brewly");
    try (Stream<Path> files = Files.list(tenantDir)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
        docs.add(markdownDoc("brewly", file));
      }
    }
    return docs;
  }

  private static List<Doc> finflowDocs() throws IOException {
    List<Doc> docs = new ArrayList<>();
    try (Stream<Path> files = Files.list(FINFLOW_DIR)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
        docs.add(markdownDoc("finflow", file));
      }
    }
    return docs;
  }

  private static Doc markdownDoc(String tenant, Path file) throws IOException {
    String content = Files.readString(file, StandardCharsets.UTF_8);
    List<Section> sections = new ArrayList<>();
    String heading = "";
    StringBuilder body = new StringBuilder();
    String title = null;

    for (String line : content.split("\n")) {
      if (line.startsWith("#")) {
        if (body.length() > 0 || !heading.isEmpty()) {
          sections.add(new Section(heading, body.toString().trim()));
        }
        heading = line.replaceFirst("^#+\\s*", "").trim();
        if (title == null) {
          title = heading;
        }
        body = new StringBuilder();
      } else if (!line.isBlank()) {
        body.append(line.trim()).append('\n');
      }
    }
    sections.add(new Section(heading, body.toString().trim()));

    String filename = file.getFileName().toString();
    return new Doc(tenant, filename, title == null ? filename : title, content, sections);
  }

  /** Splits the page into sections at h1, h2 and h3, using the same main element as the crawler. */
  static List<Section> htmlSections(String html, String title) {
    org.jsoup.nodes.Document document = Jsoup.parse(html);
    Element root = document.selectFirst("main");
    if (root == null) {
      root = document.body();
    }

    List<Section> sections = new ArrayList<>();
    String heading = "";
    StringBuilder body = new StringBuilder();

    for (Element element : root.select("h1, h2, h3, p, li, address")) {
      String text = element.text().trim();
      if (text.isEmpty()) {
        continue;
      }
      if (element.tagName().matches("h[123]")) {
        if (body.length() > 0 || !heading.isEmpty()) {
          sections.add(new Section(heading, body.toString().trim()));
        }
        heading = text;
        body = new StringBuilder();
      } else {
        body.append(text).append('\n');
      }
    }
    sections.add(new Section(heading, body.toString().trim()));
    return sections;
  }

  private static EmbeddingModel newEmbeddingModel() throws Exception {
    TransformersEmbeddingModel model = new TransformersEmbeddingModel();
    model.afterPropertiesSet();
    return model;
  }
}
