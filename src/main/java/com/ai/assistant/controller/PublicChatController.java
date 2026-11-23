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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous chat endpoint for public website widgets, one path per tenant.
 *
 * <p>Visitors only ask about public information, so this endpoint needs no login. It only
 * answers for tenants with {@code public_chat_enabled} set, and only runs the knowledge (RAG)
 * path. Ticket, draft and image features stay behind authenticated endpoints.
 */
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

    if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
      throw new RateLimitExceededException();
    }

    Tenant tenant = tenantService.findActiveBySlug(tenantSlug)
        .filter(Tenant::isPublicChatEnabled)
        .orElseThrow(PublicChatUnavailableException::new);

    String answer = assistantService.answerPublic(
        request.conversationId(),
        request.question(),
        tenant);

    return new PublicChatResponse(answer);
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, String>> rateLimited() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("error", "Too many questions in a short time. Please wait a moment."));
  }

  // Unknown tenants and tenants without public chat look the same, so the endpoint does not
  // reveal which tenants exist.
  @ExceptionHandler(PublicChatUnavailableException.class)
  public ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
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
