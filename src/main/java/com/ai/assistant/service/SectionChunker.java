package com.ai.assistant.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a page or a markdown document into chunks along its headings.
 *
 * <p>A chunk is one section, or several small neighbouring sections merged together, so that a
 * chunk is about one topic. Every chunk starts with a label, "page title › heading", so a short
 * chunk still carries its context when it is embedded.
 *
 * <p>Rules (measured against the Brewly and FinFlow content, see the evaluation test):
 * <ul>
 *   <li>merge neighbouring sections until a chunk reaches {@link #MIN_CHUNK_CHARS};</li>
 *   <li>split any chunk longer than {@link #MAX_CHUNK_CHARS} at a word boundary;</li>
 *   <li>prefix every chunk with its label.</li>
 * </ul>
 *
 * <p>{@link #VERSION} is part of the content hash used to skip unchanged documents. Change it
 * whenever these rules change, so that the next crawl or load re-indexes every document.
 */
public final class SectionChunker {

  /** Identifies the chunking rules. Bump it when the rules change. */
  public static final String VERSION = "sections-v2";

  static final int MIN_CHUNK_CHARS = 250;
  static final int MAX_CHUNK_CHARS = 800;

  /** One heading and the text under it. The heading is empty for text before the first heading. */
  public record Section(String heading, String body) {
  }

  private record Piece(String label, String text) {
  }

  private SectionChunker() {
  }

  /**
   * @param pageTitle the title of the page or document, used as the first part of each label
   * @param sections  the sections in document order
   * @return the chunk texts, each one starting with its label
   */
  public static List<String> chunk(String pageTitle, List<Section> sections) {
    List<Piece> merged = mergeSmallSections(pageTitle, sections);

    List<String> chunks = new ArrayList<>();
    for (Piece piece : merged) {
      String label = piece.label();
      if (label.length() + 1 + piece.text().length() <= MAX_CHUNK_CHARS) {
        chunks.add(label + "\n" + piece.text());
        continue;
      }
      // Leave room for the label and the line break after it.
      int budget = MAX_CHUNK_CHARS - label.length() - 1;
      for (String part : splitAtWords(piece.text(), budget)) {
        chunks.add(label + "\n" + part);
      }
    }
    return chunks;
  }

  /**
   * Splits markdown at its "#" headings. Lines before the first heading form a section with an
   * empty heading.
   */
  public static List<Section> fromMarkdown(String markdown) {
    List<Section> sections = new ArrayList<>();
    String heading = "";
    StringBuilder body = new StringBuilder();

    for (String line : markdown.split("\n")) {
      if (line.startsWith("#")) {
        addIfNotEmpty(sections, heading, body);
        heading = line.replaceFirst("^#+\\s*", "").trim();
        body = new StringBuilder();
      } else if (!line.isBlank()) {
        body.append(line.trim()).append('\n');
      }
    }
    addIfNotEmpty(sections, heading, body);
    return sections;
  }

  /** The first heading of a markdown document, or the fallback (for example the file name). */
  public static String titleOf(List<Section> sections, String fallback) {
    return sections.stream()
        .map(Section::heading)
        .filter(heading -> !heading.isEmpty())
        .findFirst()
        .orElse(fallback);
  }

  private static List<Piece> mergeSmallSections(String pageTitle, List<Section> sections) {
    List<Piece> pieces = new ArrayList<>();
    StringBuilder pending = null;
    String pendingHeading = "";

    for (Section section : sections) {
      if (pending == null) {
        // The label already names the first heading, so the text starts with the body.
        pending = new StringBuilder(section.body());
        pendingHeading = section.heading();
      } else {
        // A later section in the same chunk is not in the label, so its heading stays in the text.
        pending.append('\n');
        if (!section.heading().isEmpty()) {
          pending.append(section.heading()).append('\n');
        }
        pending.append(section.body());
      }

      if (pending.length() >= MIN_CHUNK_CHARS) {
        pieces.add(new Piece(labelOf(pageTitle, pendingHeading), pending.toString()));
        pending = null;
      }
    }
    if (pending != null) {
      pieces.add(new Piece(labelOf(pageTitle, pendingHeading), pending.toString()));
    }
    return pieces;
  }

  /** "page › heading", or just the page title when there is no heading or it repeats the title. */
  static String labelOf(String pageTitle, String heading) {
    if (heading.isEmpty() || heading.equals(pageTitle)) {
      return pageTitle;
    }
    return pageTitle + " › " + heading;
  }

  /** Cuts text into parts of at most {@code maxChars}, preferring a space as the cut point. */
  static List<String> splitAtWords(String text, int maxChars) {
    List<String> parts = new ArrayList<>();
    String rest = text.trim();

    while (rest.length() > maxChars) {
      int cut = rest.lastIndexOf(' ', maxChars);
      if (cut <= 0) {
        cut = maxChars;
      }
      parts.add(rest.substring(0, cut).trim());
      rest = rest.substring(cut).trim();
    }
    if (!rest.isEmpty()) {
      parts.add(rest);
    }
    return parts;
  }

  private static void addIfNotEmpty(List<Section> sections, String heading, StringBuilder body) {
    String text = body.toString().trim();
    if (!heading.isEmpty() || !text.isEmpty()) {
      sections.add(new Section(heading, text));
    }
  }
}
