
package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.ai.assistant.service.WebPageExtractor.ExtractedPage;

class WebPageExtractorTest {

  private final WebPageExtractor extractor = new WebPageExtractor();

  @Test
  void extractsTitleAndCategory() throws IOException {
    ExtractedPage page = extractor.extract(
        "http://localhost:3000/products.html",
        loadFixture("products-page.html"));

    assertEquals("Products - Test Coffee", page.title());
    assertEquals("products", page.category());
    assertEquals("http://localhost:3000/products.html", page.url());
  }

  @Test
  void keepsMainContentAndRemovesNoise() throws IOException {
    ExtractedPage page = extractor.extract(
        "http://localhost:3000/products.html",
        loadFixture("products-page.html"));

    assertTrue(page.text().contains("Our coffees"));
    assertTrue(page.text().contains("Brewly House Blend"));

    assertFalse(page.text().contains("NAV_MARKER"), "nav must be removed");
    assertFalse(page.text().contains("FOOTER_MARKER"), "footer must be removed");
    assertFalse(page.text().contains("SCRIPT_MARKER"), "script must be removed");
    assertFalse(page.text().contains("STYLE_MARKER"), "style must be removed");
    assertFalse(page.text().contains("FORM_MARKER"), "form must be removed");
  }

  @Test
  void fallsBackToBodyWhenThereIsNoMainElement() throws IOException {
    ExtractedPage page = extractor.extract(
        "http://localhost:3000/shipping.html",
        loadFixture("no-main-page.html"));

    assertEquals("Shipping", page.title());
    assertEquals("", page.category(), "no meta tag means an empty category");
    assertTrue(page.text().contains("Delivery takes 2-3 business days"));

    assertFalse(page.text().contains("NAV_MARKER"));
    assertFalse(page.text().contains("FOOTER_MARKER"));
    assertFalse(page.text().contains("SCRIPT_MARKER"));
  }

  @Test
  void returnsEmptyFieldsForAnEmptyPage() throws IOException {
    ExtractedPage page = extractor.extract(
        "http://localhost:3000/empty.html",
        loadFixture("empty-page.html"));

    assertEquals("", page.title());
    assertEquals("", page.category());
    assertEquals("", page.text());
  }

  private String loadFixture(String name) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/crawler/" + name)) {
      if (in == null) {
        throw new IOException("Missing test fixture: " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
