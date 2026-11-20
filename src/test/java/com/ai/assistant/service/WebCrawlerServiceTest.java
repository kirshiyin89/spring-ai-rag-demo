
package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ai.assistant.service.WebPageExtractor.ExtractedPage;
import com.sun.net.httpserver.HttpServer;

class WebCrawlerServiceTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startSiteServer() throws IOException {
    // A tiny local web server that serves the fixture files in crawler/site/.
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      // "/" is served as index.html, the same as a real web server would do.
      String path = exchange.getRequestURI().getPath();
      String fileName = path.equals("/") ? "index.html" : path.substring(1);
      try (InputStream in = getClass().getResourceAsStream("/crawler/site/" + fileName)) {
        if (in == null) {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
          return;
        }
        byte[] body = in.readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(body);
        }
      }
    });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopSiteServer() {
    server.stop(0);
  }

  @Test
  void visitsEveryLinkedPageExactlyOnce() {
    WebCrawlerService crawler = crawlerWith(50, 5);

    List<ExtractedPage> pages = crawler.crawl(baseUrl + "/index.html");

    Set<String> paths = pagePaths(pages);
    assertEquals(Set.of("/index.html", "/products.html", "/faq.html"), paths);
    assertEquals(paths.size(), pages.size(), "each page must be returned once");
  }

  @Test
  void skipsUnlinkedPagesOffSiteLinksAndBrokenLinks() {
    WebCrawlerService crawler = crawlerWith(50, 5);

    List<ExtractedPage> pages = crawler.crawl(baseUrl + "/index.html");

    assertFalse(pagePaths(pages).contains("/orphan.html"), "nothing links to orphan.html");
    assertFalse(pagePaths(pages).contains("/missing.html"), "missing.html returns 404");
    for (ExtractedPage page : pages) {
      assertTrue(page.url().startsWith(baseUrl + "/"), "must stay on the site: " + page.url());
    }
  }

  @Test
  void extractsTitleAndTextOfEachPage() {
    WebCrawlerService crawler = crawlerWith(50, 5);

    List<ExtractedPage> pages = crawler.crawl(baseUrl + "/index.html");

    ExtractedPage products = pages.stream()
        .filter(page -> page.url().endsWith("/products.html"))
        .findFirst()
        .orElseThrow();
    assertEquals("Products", products.title());
    assertEquals("Our coffees.", products.text());
  }

  @Test
  void stopsAtTheMaxPageLimit() {
    WebCrawlerService crawler = crawlerWith(2, 5);

    List<ExtractedPage> pages = crawler.crawl(baseUrl + "/index.html");

    assertEquals(2, pages.size());
  }

  @Test
  void doesNotFollowLinksBeyondTheMaxDepth() {
    WebCrawlerService crawler = crawlerWith(50, 0);

    List<ExtractedPage> pages = crawler.crawl(baseUrl + "/index.html");

    assertEquals(Set.of("/index.html"), pagePaths(pages));
  }

  @Test
  void reportsSiteUnavailableWhenTheStartPageIsMissing() {
    WebCrawlerService crawler = crawlerWith(50, 5);

    assertThrows(CrawlSiteUnavailableException.class,
        () -> crawler.crawl(baseUrl + "/no-such-page.html"));
  }

  @Test
  void reportsSiteUnavailableWhenTheServerIsOffline() {
    WebCrawlerService crawler = crawlerWith(50, 5);

    // Nothing listens on port 1, so the connection is refused.
    assertThrows(CrawlSiteUnavailableException.class,
        () -> crawler.crawl("http://localhost:1/"));
  }

  @Test
  void rejectsStartUrlsThatAreNotHttp() {
    WebCrawlerService crawler = crawlerWith(50, 5);

    assertThrows(IllegalArgumentException.class, () -> crawler.crawl("file:///etc/passwd"));
    assertThrows(IllegalArgumentException.class, () -> crawler.crawl("/relative/path.html"));
  }

  private WebCrawlerService crawlerWith(int maxPages, int maxDepth) {
    return new WebCrawlerService(new WebPageExtractor(), maxPages, maxDepth, TIMEOUT);
  }

  private static Set<String> pagePaths(List<ExtractedPage> pages) {
    return pages.stream()
        .map(page -> URI.create(page.url()).getPath())
        .collect(Collectors.toSet());
  }

}
