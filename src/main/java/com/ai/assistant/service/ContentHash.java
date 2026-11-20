package com.ai.assistant.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 fingerprints of document content, used to skip unchanged documents during ingestion. */
public final class ContentHash {

  private ContentHash() {
  }

  public static String sha256(String content) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
    }
  }
}
