package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.assistant.service.SectionChunker.Section;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SectionChunkerTest {

  @Test
  void mergesSmallNeighbouringSectionsIntoOneChunk() {
    List<Section> sections = List.of(
        new Section("Email", "Write to support@brewly-coffee.example."),
        new Section("Phone", "Call +36 1 555 0123."),
        new Section("Postal address", "Kávé utca 12, 1051 Budapest."),
        new Section("Opening hours", "Monday to Friday: 09:00–17:00.")
    );

    List<String> chunks = SectionChunker.chunk("Contact – Brewly Coffee", sections);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst())
        .contains("support@brewly-coffee.example")
        .contains("+36 1 555 0123")
        .contains("Monday to Friday");
  }

  @Test
  void doesNotRepeatTheHeadingOfTheFirstSectionInsideTheChunk() {
    List<Section> sections = List.of(
        new Section("Email", "Write to support@brewly-coffee.example."),
        new Section("Phone", "Call +36 1 555 0123.")
    );

    List<String> chunks = SectionChunker.chunk("Contact", sections);

    // The label names "Email" once, the text does not repeat it, and "Phone" stays in the text.
    assertThat(chunks).containsExactly(
        "Contact › Email\nWrite to support@brewly-coffee.example.\nPhone\nCall +36 1 555 0123.");
  }

  @Test
  void keepsSectionsApartOnceTheMinimumSizeIsReached() {
    String longBody = "x".repeat(SectionChunker.MIN_CHUNK_CHARS);
    List<Section> sections = List.of(
        new Section("Returns", longBody),
        new Section("Refunds", "Refunds take five business days.")
    );

    List<String> chunks = SectionChunker.chunk("Returns", sections);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(1)).contains("five business days");
  }

  @Test
  void prefixesEveryChunkWithPageAndHeading() {
    List<Section> sections = List.of(
        new Section("Opening hours", "Monday to Friday: 09:00–17:00. ".repeat(12))
    );

    List<String> chunks = SectionChunker.chunk("Contact – Brewly Coffee", sections);

    assertThat(chunks).allSatisfy(chunk ->
        assertThat(chunk).startsWith("Contact – Brewly Coffee › Opening hours\n"));
  }

  @Test
  void labelDoesNotRepeatThePageTitleWhenTheHeadingIsTheTitle() {
    assertThat(SectionChunker.labelOf("VPN and WLAN Access", "VPN and WLAN Access"))
        .isEqualTo("VPN and WLAN Access");
    assertThat(SectionChunker.labelOf("Contact", ""))
        .isEqualTo("Contact");
  }

  @Test
  void splitsLongSectionsWithinTheMaximumSizeAndLosesNoWords() {
    String body = "word ".repeat(600).trim();
    List<Section> sections = List.of(new Section("Long section", body));

    List<String> chunks = SectionChunker.chunk("Guide", sections);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks).allSatisfy(chunk ->
        assertThat(chunk.length()).isLessThanOrEqualTo(SectionChunker.MAX_CHUNK_CHARS));

    String restored = String.join(" ", chunks).replace("Guide › Long section\n", "");
    long words = Arrays.stream(restored.split("\\s+"))
        .filter("word"::equals)
        .count();
    assertThat(words).isEqualTo(600);
  }

  @Test
  void returnsNoChunksForNoSections() {
    assertThat(SectionChunker.chunk("Empty", List.of())).isEmpty();
  }

  @Test
  void parsesMarkdownAtItsHeadings() {
    String markdown = """
        Intro line before any heading.

        # Shipping policy

        Delivery takes two to three days.

        ## Free delivery

        Orders above 15,000 HUF ship free.
        """;

    List<Section> sections = SectionChunker.fromMarkdown(markdown);

    assertThat(sections).extracting(Section::heading)
        .containsExactly("", "Shipping policy", "Free delivery");
    assertThat(sections.get(2).body()).isEqualTo("Orders above 15,000 HUF ship free.");
    assertThat(SectionChunker.titleOf(sections, "shipping-policy.md"))
        .isEqualTo("Shipping policy");
  }

  @Test
  void usesTheFallbackTitleWhenMarkdownHasNoHeading() {
    List<Section> sections = SectionChunker.fromMarkdown("Just some text.\n");

    assertThat(SectionChunker.titleOf(sections, "notes.md")).isEqualTo("notes.md");
  }
}
