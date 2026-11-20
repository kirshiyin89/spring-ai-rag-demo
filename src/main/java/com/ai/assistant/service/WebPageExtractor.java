
package com.ai.assistant.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Turns raw HTML into the readable content of a page.
 *
 * <p>This class does no network calls and no storage. It only parses HTML,
 * so it can be tested with saved fixture files.
 */
@Component
public class WebPageExtractor {

  /** Elements that carry no page content for a chatbot. */
  private static final String NOISE_SELECTOR =
      "script, style, noscript, nav, header, footer, form, iframe";

  /**
   * Extracts the title, category and main text of a page.
   *
   * @param url  the page URL, used by Jsoup to resolve relative links
   * @param html the raw HTML of the page
   * @return the extracted page content; {@code title} and {@code category}
   *         are empty strings when the page does not provide them
   */
  public ExtractedPage extract(String url, String html) {
    Document document = Jsoup.parse(html, url);

    String title = document.title().trim();
    String category = document.select("meta[name=category]")
        .attr("content")
        .trim();

    // Prefer <main>, which is where the content lives in the Brewly pages.
    // Fall back to <body> for pages without <main>.
    Element root = document.selectFirst("main");
    if (root == null) {
      root = document.body();
    }

    // Remove noise only inside the chosen root, so we never touch the
    // document outside the content we are going to keep.
    root.select(NOISE_SELECTOR).remove();

    // Element.text() already collapses runs of whitespace into single spaces.
    String text = root.text();

    return new ExtractedPage(url, title, category, text);
  }

  /**
   * The readable content of one web page.
   *
   * @param url      the page URL
   * @param title    the content of the {@code <title>} element
   * @param category the value of {@code <meta name="category">}, or empty
   * @param text     the main text of the page, without navigation or scripts
   */
  public record ExtractedPage(
      String url,
      String title,
      String category,
      String text) {
  }
}
