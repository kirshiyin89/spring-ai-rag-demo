package com.ai.assistant.controller;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.PublicChatRateLimiter;
import com.ai.assistant.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Anonymous chat endpoint for public website widgets, one path per tenant.
 *
 * <p>Visitors only ask about public information, so this endpoint needs no login. It only
 * answers for tenants with {@code public_chat_enabled} set, and only runs the knowledge (RAG)
 * path. Ticket, draft and image features stay behind authenticated endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/public/{tenantSlug}")
@RequiredArgsConstructor
public class PublicChatController {

  static final int MAX_QUESTION_LENGTH = 500;

  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private final AssistantService assistantService;
  private final TenantService tenantService;
  private final PublicChatRateLimiter rateLimiter;

  @PostMapping(path = "chat",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public PublicChatResponse chat(@PathVariable String tenantSlug,
      @Valid @RequestBody PublicChatRequest request,
      HttpServletRequest httpRequest) {

    Tenant tenant = admit(tenantSlug, httpRequest);

    String answer = assistantService.answerPublic(
        request.conversationId(),
        request.question(),
        tenant);

    return new PublicChatResponse(answer);
  }

  /**
   * Same question as {@link #chat}, answered as Server-Sent Events. Each text chunk arrives as a
   * {@code token} event, then one {@code done} event. If the model fails midway, the client gets
   * an {@code error} event instead of a silent cut-off.
   */
  @PostMapping(path = "chat/stream",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<StreamChunk>> chatStream(@PathVariable String tenantSlug,
      @Valid @RequestBody PublicChatRequest request,
      HttpServletRequest httpRequest) {

    Tenant tenant = admit(tenantSlug, httpRequest);

    return assistantService.streamPublic(request.conversationId(), request.question(), tenant)
        .map(text -> ServerSentEvent.builder(new StreamChunk(text)).event("token").build())
        .concatWith(Flux.just(
            ServerSentEvent.builder(new StreamChunk("")).event("done").build()))
        .onErrorResume(e -> {
          log.warn("Streaming answer failed for tenant {}", tenantSlug, e);
          return Flux.just(ServerSentEvent.builder(
                  new StreamChunk("The answer could not be completed. Please try again."))
              .event("error")
              .build());
        });
  }

  // Rate limit first, then the tenant gate. Both endpoints share this, so they cannot differ.
  private Tenant admit(String tenantSlug, HttpServletRequest httpRequest) {
    if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
      throw new RateLimitExceededException();
    }

    return tenantService.findActiveBySlug(tenantSlug)
        .filter(Tenant::isPublicChatEnabled)
        .orElseThrow(PublicChatUnavailableException::new);
  }

  // The stream endpoint accepts only text/event-stream, so these error bodies set their JSON
  // content type explicitly. Otherwise Spring would reject them with 406 instead of sending the
  // message.
  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, String>> rateLimited() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("error", "Too many questions in a short time. Please wait a moment."));
  }

  // Unknown tenants and tenants without public chat look the same, so the endpoint does not
  // reveal which tenants exist.
  @ExceptionHandler(PublicChatUnavailableException.class)
  public ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("error", "Chat is not available here."));
  }

  public record PublicChatRequest(
      @NotBlank
      @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
      String conversationId,

      @NotBlank
      @Size(max = MAX_QUESTION_LENGTH)
      String question) {
  }

  public record PublicChatResponse(String answer) {
  }

  /** Payload of one SSE event. Wrapping the text in JSON keeps newlines inside the event intact. */
  public record StreamChunk(String text) {
  }

  static class RateLimitExceededException extends RuntimeException {
    RateLimitExceededException() {
      super("Rate limit exceeded");
    }
  }

  static class PublicChatUnavailableException extends RuntimeException {
    PublicChatUnavailableException() {
      super("Public chat is not available for this tenant");
    }
  }
}
