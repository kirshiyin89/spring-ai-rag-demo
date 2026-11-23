package com.ai.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.PublicChatRateLimiter;
import com.ai.assistant.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Checks which SSE events the stream sends, without a web server. MockMvc cannot reliably read
 * async streams, so the event mapping is tested on the returned Flux directly.
 */
class PublicChatStreamEventsTest {

  private static final String CONVERSATION_ID = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

  private final AssistantService assistantService = mock(AssistantService.class);
  private final TenantService tenantService = mock(TenantService.class);
  private final PublicChatRateLimiter rateLimiter = mock(PublicChatRateLimiter.class);
  private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

  private final PublicChatController controller =
      new PublicChatController(assistantService, tenantService, rateLimiter);

  private final PublicChatController.PublicChatRequest request =
      new PublicChatController.PublicChatRequest(CONVERSATION_ID, "How long does delivery take?");

  PublicChatStreamEventsTest() throws Exception {
    Tenant brewly = new Tenant();
    brewly.setId(1L);
    brewly.setSlug("brewly");
    brewly.setPublicChatEnabled(true);
    when(tenantService.findActiveBySlug("brewly")).thenReturn(Optional.of(brewly));
    when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
  }

  @Test
  void shouldEmitEachChunkAsATokenThenDone() throws Exception {
    when(assistantService.streamPublic(anyString(), anyString(), any(Tenant.class)))
        .thenReturn(Flux.just("Delivery ", "takes 2-3 days."));

    List<ServerSentEvent<PublicChatController.StreamChunk>> events = collect();

    assertThat(events).extracting(ServerSentEvent::event)
        .containsExactly("token", "token", "done");
    assertThat(events.get(0).data().text()).isEqualTo("Delivery ");
    assertThat(events.get(1).data().text()).isEqualTo("takes 2-3 days.");
    assertThat(events.get(2).data().text()).isEmpty();
  }

  @Test
  void shouldEndWithAnErrorEventInsteadOfDoneWhenTheModelFails() throws Exception {
    when(assistantService.streamPublic(anyString(), anyString(), any(Tenant.class)))
        .thenReturn(Flux.concat(
            Flux.just("Partial "),
            Flux.error(new IllegalStateException("internal detail"))));

    List<ServerSentEvent<PublicChatController.StreamChunk>> events = collect();

    assertThat(events).extracting(ServerSentEvent::event)
        .containsExactly("token", "error");
    // The client gets a safe message, never the internal exception text.
    assertThat(events.get(1).data().text())
        .isEqualTo("The answer could not be completed. Please try again.")
        .doesNotContain("internal detail");
  }

  // Runs the controller method and waits for the whole stream, the way the servlet would.
  private List<ServerSentEvent<PublicChatController.StreamChunk>> collect() {
    return controller.chatStream("brewly", request, httpRequest)
        .collectList()
        .block();
  }
}
