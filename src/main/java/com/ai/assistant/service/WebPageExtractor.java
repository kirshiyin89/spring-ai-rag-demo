
package com.ai.assistant.service;

import com.ai.assistant.service.SectionChunker.Section;
import java.util.ArrayList;
import java.util.List;
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

    // Sections keep the headings, which the chunker needs. Built from the same root.
    List<Section> sections = sectionsOf(root);

    // Element.text() already collapses runs of whitespace into single spaces.
    String text = root.text();

    return new ExtractedPage(url, title, category, text, sections);
  }

  /**
   * Splits the content of the root into sections at h1, h2 and h3. Each paragraph, list item
   * and address line is kept as its own line of text, so no text is repeated across sections.
   */
  private static List<Section> sectionsOf(Element root) {
    List<Section> sections = new ArrayList<>();
    String heading = "";
    StringBuilder body = new StringBuilder();

    for (Element element : root.select("h1, h2, h3, p, li, address")) {
      String text = element.text().trim();
      if (text.isEmpty()) {
        continue;
      }

      if (element.tagName().matches("h[123]")) {
        if (!heading.isEmpty() || body.length() > 0) {
          sections.add(new Section(heading, body.toString().trim()));
        }
        heading = text;
        body = new StringBuilder();
      } else {
        body.append(text).append('\n');
      }
    }

    if (!heading.isEmpty() || body.length() > 0) {
      sections.add(new Section(heading, body.toString().trim()));
    }
    return sections;
  }

  /**
   * The readable content of one web page.
   *
   * @param url      the page URL
   * @param title    the content of the {@code <title>} element
   * @param category the value of {@code <meta name="category">}, or empty
   * @param text     the main text of the page, without navigation or scripts; used for the
   *                 content hash
   * @param sections the same content split at its headings, used for chunking
   */
  public record ExtractedPage(
      String url,
      String title,
      String category,
      String text,
      List<Section> sections) {

    /** A page without sections; the chunker then uses the whole text as one section. */
    public ExtractedPage(String url, String title, String category, String text) {
      this(url, title, category, text, List.of());
    }
  }
}
