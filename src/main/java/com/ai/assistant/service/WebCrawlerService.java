
package com.ai.assistant.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ai.assistant.service.WebPageExtractor.ExtractedPage;

import lombok.extern.slf4j.Slf4j;

/**
 * Crawls one website, starting from a URL, and returns the content of every page it finds.
 *
 * <p>The crawl is a breadth-first search over the links on each page. It stays on the
 * scheme, host and port of the start URL, so it never leaves the site. Each page is
 * visited at most once, and the crawl stops at the configured page and depth limits.
 *
 * <p>This class only fetches and extracts. It does not store anything.
 */
@Slf4j
@Component
public class WebCrawlerService {

  private final WebPageExtractor extractor;
  private final HttpClient httpClient;
  private final int maxPages;
  private final int maxDepth;
  private final Duration requestTimeout;

  public WebCrawlerService(
      WebPageExtractor extractor,
      @Value("${app.crawler.max-pages:50}") int maxPages,
      @Value("${app.crawler.max-depth:5}") int maxDepth,
      @Value("${app.crawler.request-timeout:10s}") Duration requestTimeout) {
    this.extractor = extractor;
    this.maxPages = maxPages;
    this.maxDepth = maxDepth;
    this.requestTimeout = requestTimeout;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(requestTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  /**
   * Crawls the site that the start URL belongs to.
   *
   * @param startUrl an absolute http or https URL, for example {@code http://localhost:3000/}
   * @return the extracted content of each page found, in the order they were visited
   * @throws IllegalArgumentException if the start URL is not an absolute http or https URL
   * @throws CrawlSiteUnavailableException if the start page cannot be downloaded
   */
  public List<ExtractedPage> crawl(String startUrl) {
    URI start = parseHttpUrl(startUrl);
    String origin = originOf(start);

    List<ExtractedPage> pages = new ArrayList<>();
    Set<URI> visited = new HashSet<>();
    // Different URLs can serve the same page, for example "/" and "/index.html".
    // Visited URLs cannot catch that, so we also remember the content we have kept.
    Set<String> seenContent = new HashSet<>();
    Deque<CrawlTarget> queue = new ArrayDeque<>();

    visited.add(start);
    queue.add(new CrawlTarget(start, 0));

    while (!queue.isEmpty() && pages.size() < maxPages && !Thread.currentThread().isInterrupted()) {
      CrawlTarget target = queue.poll();

      Optional<String> html = fetchHtml(target.uri(), origin);
      if (html.isEmpty()) {
        if (target.depth() == 0) {
          // Without the start page there is nothing to crawl. Report it so the caller can retry.
          throw new CrawlSiteUnavailableException("Start page could not be fetched: " + start);
        }
        continue;
      }

      ExtractedPage page = extractor.extract(target.uri().toString(), html.get());
      if (seenContent.add(page.title() + "\n" + page.text())) {
        pages.add(page);
      } else {
        log.debug("Not keeping {}: same content as a page already extracted", target.uri());
      }

      if (target.depth() >= maxDepth) {
        continue;
      }
      for (URI link : linksOn(target.uri(), html.get(), origin)) {
        if (visited.add(link)) {
          queue.add(new CrawlTarget(link, target.depth() + 1));
        }
      }
    }

    log.info("Crawl of {} finished: {} pages extracted, {} URLs seen", origin, pages.size(), visited.size());
    return pages;
  }

  /**
   * Downloads one page. Returns empty when the page is not a usable HTML page.
   * Skipped pages are logged and do not stop the crawl.
   */
  private Optional<String> fetchHtml(URI uri, String origin) {
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(requestTimeout)
        .header("Accept", "text/html")
        .GET()
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.warn("Skipping {}: HTTP {}", uri, response.statusCode());
        return Optional.empty();
      }

      String contentType = response.headers().firstValue("Content-Type").orElse("");
      if (!contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
        log.debug("Skipping {}: content type is '{}'", uri, contentType);
        return Optional.empty();
      }

      // Redirects are followed by the client, so check where the response really came from.
      if (!origin.equals(originOf(response.uri()))) {
        log.warn("Skipping {}: redirected outside {} to {}", uri, origin, response.uri());
        return Optional.empty();
      }

      return Optional.ofNullable(response.body());
    } catch (IOException e) {
      log.warn("Skipping {}: {}", uri, e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /**
   * Finds the links on a page that belong to the same site.
   * Links are read from the whole page, including navigation, because that is where
   * most links live. The extractor removes navigation from the text, but not from here.
   */
  private List<URI> linksOn(URI page, String html, String origin) {
    Document document = Jsoup.parse(html, page.toString());
    List<URI> links = new ArrayList<>();

    for (Element anchor : document.select("a[href]")) {
      String absolute = anchor.absUrl("href");
      if (absolute.isEmpty()) {
        continue;
      }
      URI link;
      try {
        link = withoutFragment(URI.create(absolute));
      } catch (IllegalArgumentException e) {
        continue; // malformed link, ignore it
      }
      if (isHttpUrl(link) && origin.equals(originOf(link))) {
        links.add(link);
      }
    }
    return links;
  }

  private static URI parseHttpUrl(String url) {
    URI uri;
    try {
      uri = withoutFragment(URI.create(url));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Start URL is not a valid URL: " + url, e);
    }
    if (!isHttpUrl(uri)) {
      throw new IllegalArgumentException("Start URL must be an absolute http or https URL: " + url);
    }
    return uri;
  }

  private static boolean isHttpUrl(URI uri) {
    String scheme = uri.getScheme();
    return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
        && uri.getAuthority() != null;
  }

  /** Scheme, host and port, for example {@code http://localhost:3000}. */
  private static String originOf(URI uri) {
    return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getAuthority().toLowerCase(Locale.ROOT);
  }

  /** Removes the {@code #fragment} part, so {@code products.html#top} and {@code products.html} are one page. */
  private static URI withoutFragment(URI uri) {
    String text = uri.toString();
    int hash = text.indexOf('#');
    return URI.create(hash >= 0 ? text.substring(0, hash) : text);
  }

  /** A page waiting to be fetched, with how many links it is away from the start URL. */
  private record CrawlTarget(URI uri, int depth) {
  }
}
