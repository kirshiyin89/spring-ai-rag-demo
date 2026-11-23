package com.ai.assistant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantService;
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

@SpringBootTest
@AutoConfigureMockMvc
class PublicChatControllerTest {

  private static final String CHAT_URL = "/api/public/brewly/chat";
  private static final String CONVERSATION_ID = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
  private static final String ALLOWED_ORIGIN = "http://localhost:3000";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AssistantService assistantService;

  @MockitoBean
  private TenantService tenantService;

  private Tenant brewly;

  @BeforeEach
  void setUp() {
    brewly = new Tenant();
    brewly.setId(1L);
    brewly.setSlug("brewly");
    brewly.setPublicChatEnabled(true);
    when(tenantService.findActiveBySlug("brewly")).thenReturn(Optional.of(brewly));
  }

  @Test
  void shouldAnswerAnonymousQuestionForAPublicTenant() throws Exception {
    when(assistantService.answerPublic(anyString(), anyString(), any(Tenant.class)))
        .thenReturn("We ship within 3 business days.");

    mockMvc.perform(post(CHAT_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .content("""
                {"conversationId": "%s", "question": "How long does shipping take?"}
                """.formatted(CONVERSATION_ID)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
        .andExpect(jsonPath("$.answer").value("We ship within 3 business days."));
  }

  @Test
  void shouldNotAnswerForATenantWithoutPublicChat() throws Exception {
    Tenant internal = new Tenant();
    internal.setSlug("finflow");
    internal.setPublicChatEnabled(false);
    when(tenantService.findActiveBySlug("finflow")).thenReturn(Optional.of(internal));

    mockMvc.perform(post("/api/public/finflow/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"conversationId": "%s", "question": "Hello"}
                """.formatted(CONVERSATION_ID)))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldNotAnswerForAnUnknownTenant() throws Exception {
    when(tenantService.findActiveBySlug("nobody")).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/public/nobody/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"conversationId": "%s", "question": "Hello"}
                """.formatted(CONVERSATION_ID)))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectBlankQuestionWithoutCallingAssistant() throws Exception {
    mockMvc.perform(post(CHAT_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"conversationId": "%s", "question": "   "}
                """.formatted(CONVERSATION_ID)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectNonUuidConversationId() throws Exception {
    mockMvc.perform(post(CHAT_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"conversationId": "not-a-uuid", "question": "Hello"}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectOverlongQuestion() throws Exception {
    String longQuestion = "a".repeat(PublicChatController.MAX_QUESTION_LENGTH + 1);

    mockMvc.perform(post(CHAT_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"conversationId": "%s", "question": "%s"}
                """.formatted(CONVERSATION_ID, longQuestion)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldAllowCorsPreflightFromWidgetOrigin() throws Exception {
    mockMvc.perform(options(CHAT_URL)
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
  }

  @Test
  void shouldNotAllowCorsFromUnknownOrigin() throws Exception {
    mockMvc.perform(options(CHAT_URL)
            .header(HttpHeaders.ORIGIN, "https://evil.example")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void shouldKeepAuthenticatedRagEndpointProtected() throws Exception {
    mockMvc.perform(get("/api/chat/rag")
            .param("conversationId", CONVERSATION_ID)
            .param("question", "Hello"))
        .andExpect(status().isUnauthorized());
  }
}
