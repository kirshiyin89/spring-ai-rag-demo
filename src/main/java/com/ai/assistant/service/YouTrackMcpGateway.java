package com.ai.assistant.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class YouTrackMcpGateway implements AutoCloseable {

  private final String url;
  private final String endpoint;
  private final Duration requestTimeout;

  private McpSyncClient client;
  private ToolCallback issueCallback;
  private Instant retryAfter = Instant.MIN;

  private static final Duration RETRY_COOLDOWN =
      Duration.ofSeconds(5);

  public YouTrackMcpGateway(
      @Value("${app.integrations.youtrack.url}") String url,
      @Value("${app.integrations.youtrack.endpoint}") String endpoint,
      @Value("${app.integrations.youtrack.request-timeout:10s}")
      Duration requestTimeout) {
    this.url = url;
    this.endpoint = endpoint;
    this.requestTimeout = requestTimeout;
  }

  public synchronized String getIssue(String issueId) {
    if (issueId == null || !issueId.matches("[A-Z][A-Z0-9]*-\\d+")) {
      throw new IllegalArgumentException("Invalid YouTrack issue ID");
    }
    try {
      ToolCallback callback = getOrCreateCallback();

      String input = "{\"issueId\":\""
          + issueId.replace("\\", "\\\\").replace("\"", "\\\"")
          + "\"}";

      return callback.call(input);
    } catch (Exception ex) {
      invalidateClient();

      retryAfter = Instant.now().plus(RETRY_COOLDOWN);

      log.warn("YouTrack lookup failed; client invalidated. "
          + "A later request can reconnect.", ex);

      throw new YouTrackUnavailableException(
          "YouTrack could not complete the issue lookup.", ex);
    }
  }

  private ToolCallback getOrCreateCallback() {
    if (issueCallback != null) {
      return issueCallback;
    }

    if (Instant.now().isBefore(retryAfter)) {
      throw new YouTrackUnavailableException(
          "YouTrack is in its retry cooldown.", null);
    }

    try {
      var transport = HttpClientStreamableHttpTransport
          .builder(url)
          .endpoint(endpoint)
          .build();

      client = McpClient.sync(transport)
          .clientInfo(new McpSchema.Implementation(
              "finflow-assistant", "0.0.1"))
          .requestTimeout(requestTimeout)
          .build();

      client.initialize();

      issueCallback = Arrays.stream(
              new SyncMcpToolCallbackProvider(client)
                  .getToolCallbacks())
          .filter(tool -> "getYouTrackIssue".equals(
              tool.getToolDefinition().name()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "MCP tool getYouTrackIssue was not registered"));

      log.info("YouTrack MCP client initialized successfully");
      return issueCallback;

    } catch (Exception ex) {
      invalidateClient();
      retryAfter = Instant.now().plus(RETRY_COOLDOWN);

      throw new YouTrackUnavailableException(
          "Could not initialize the YouTrack MCP client.", ex);
    }
  }

  private void invalidateClient() {
    issueCallback = null;

    if (client != null) {
      try {
        client.close();
      } catch (Exception ex) {
        log.debug("Error closing failed YouTrack client", ex);
      } finally {
        client = null;
      }
    }
  }

  @Override
  public synchronized void close() {
    invalidateClient();
  }

  public static class YouTrackUnavailableException
      extends RuntimeException {

    public YouTrackUnavailableException(
        String message, Throwable cause) {
      super(message, cause);
    }
  }
}
