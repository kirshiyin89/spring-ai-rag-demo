package com.ai.assistant.controller;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.CrawlIngestionService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous chat endpoint for the public Brewly website widget.
 *
 * <p>Visitors only ask about public coffee information, so this endpoint needs no login. It always
 * answers for the Brewly tenant and only runs the knowledge (RAG) path. Ticket, draft and image
 * features stay behind authenticated endpoints.
 */
@RestController
@RequestMapping("/api/public/brewly")
@RequiredArgsConstructor
public class PublicBrewlyChatController {

  static final int MAX_QUESTION_LENGTH = 500;

  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private final AssistantService assistantService;
  private final TenantService tenantService;
  private final PublicChatRateLimiter rateLimiter;

  @PostMapping(path = "chat",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public PublicChatResponse chat(@Valid @RequestBody PublicChatRequest request,
      HttpServletRequest httpRequest) {

    if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
      throw new RateLimitExceededException();
    }

    Tenant brewly = tenantService.resolveBySlug(CrawlIngestionService.BREWLY_SLUG);

    String answer = assistantService.askWithRag(
        request.conversationId(),
        request.question(),
        brewly);

    return new PublicChatResponse(answer);
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, String>> rateLimited() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("error", "Too many questions in a short time. Please wait a moment."));
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
}
