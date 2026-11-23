package com.ai.assistant.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.PublicChatRateLimiter;
import com.ai.assistant.service.TenantService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The streaming chat endpoint. The rate limiter is mocked here so these tests never share a
 * window with the other tests that post to the same address.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicChatStreamControllerTest {

  private static final String STREAM_URL = "/api/public/brewly/chat/stream";
  private static final String CONVERSATION_ID = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
  private static final String ALLOWED_ORIGIN = "http://localhost:3000";
  private static final String BODY = """
      {"conversationId": "%s", "question": "How long does delivery take?"}
      """.formatted(CONVERSATION_ID);

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AssistantService assistantService;

  @MockitoBean
  private TenantService tenantService;

  @MockitoBean
  private PublicChatRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    Tenant brewly = new Tenant();
    brewly.setId(1L);
    brewly.setSlug("brewly");
    brewly.setPublicChatEnabled(true);
    when(tenantService.findActiveBySlug("brewly")).thenReturn(Optional.of(brewly));
    when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
  }

  @Test
  void shouldReturnJsonErrorWhenRateLimited() throws Exception {
    when(rateLimiter.tryAcquire(anyString())).thenReturn(false);

    // The stream accepts only event-stream, but the error body must still be JSON.
    mockMvc.perform(post(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content(BODY))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"))
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void shouldReturnNotFoundForUnknownTenant() throws Exception {
    when(tenantService.findActiveBySlug("nobody")).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/public/nobody/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content(BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Chat is not available here."));
  }

  @Test
  void shouldAllowCorsPreflightFromWidgetOrigin() throws Exception {
    mockMvc.perform(options(STREAM_URL)
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,accept"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
  }
}
