package com.ai.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LandingPathTest {

  @ParameterizedTest
  @ValueSource(strings = {"/", "/brewly.html", "/index.html?tab=chat"})
  void shouldAcceptPathsOnThisSite(String path) {
    assertThat(SecurityConfig.isLocalPath(path)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://evil.example", "//evil.example", "/\\evil.example", "brewly.html", ""})
  void shouldRejectPathsThatLeaveThisSite(String path) {
    assertThat(SecurityConfig.isLocalPath(path)).isFalse();
  }

  @Test
  void shouldRejectMissingPath() {
    assertThat(SecurityConfig.isLocalPath(null)).isFalse();
  }
}
