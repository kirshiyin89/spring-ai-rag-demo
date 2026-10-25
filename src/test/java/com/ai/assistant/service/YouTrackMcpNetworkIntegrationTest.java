package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@SpringJUnitConfig(YouTrackMcpNetworkIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
    "app.integrations.youtrack.url=http://localhost:8090",
    "app.integrations.youtrack.endpoint=/mcp",
    "app.integrations.youtrack.request-timeout=10s"
})
class YouTrackMcpNetworkIntegrationTest {

  @Configuration
  static class TestConfig {

    @Bean
    YouTrackMcpGateway youTrackMcpGateway(
        @Value("${app.integrations.youtrack.url}") String url,
        @Value("${app.integrations.youtrack.endpoint}") String endpoint,
        @Value("${app.integrations.youtrack.request-timeout}") String timeout,
        @Value("${app.integrations.youtrack.shared-secret}") String sharedSecret) {

      return new YouTrackMcpGateway(
          url,
          endpoint,
          java.time.Duration.parse("PT" + timeout.replace("s", "S")),
          sharedSecret
      );
    }

    @Autowired
    private YouTrackMcpGateway youTrackMcpGateway;

    @Test
    void shouldConnectToMcpServerAndRetrieveIssue() {
      String response = youTrackMcpGateway.getIssue("DEMO-123");

      assertThat(response)
          .isNotBlank()
          .contains("DEMO-123");
    }
  }
}