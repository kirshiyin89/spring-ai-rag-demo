package com.ai.assistant.controller;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantResponse;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.CrawlAlreadyRunningException;
import com.ai.assistant.service.CrawlIngestionService;
import com.ai.assistant.service.CrawlIngestionService.CrawlSummary;
import com.ai.assistant.service.CrawlSiteUnavailableException;
import com.ai.assistant.service.TenantService;
import com.ai.assistant.service.VectorStoreService;
import java.io.IOException;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

  private final AssistantService assistantService;
  private final VectorStoreService vectorStoreService;
  private final TenantService tenantService;
  private final CrawlIngestionService crawlIngestionService;


  // Crawls one tenant's website, named by slug (for example ?tenant=brewly). The start URL
  // comes from that tenant's configuration, never from the request. Admin only.
  @PostMapping(path = "crawl")
  public CrawlSummary crawlWebsite(@RequestParam("tenant") String tenantSlug) {

    Tenant tenant = tenantService.resolveBySlug(tenantSlug);

    return crawlIngestionService.crawlWebsite(tenant);
  }

  @ExceptionHandler(CrawlSiteUnavailableException.class)
  public ResponseEntity<String> siteUnavailable(CrawlSiteUnavailableException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(exception.getMessage());
  }

  @ExceptionHandler(CrawlAlreadyRunningException.class)
  public ResponseEntity<String> crawlAlreadyRunning(CrawlAlreadyRunningException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
  }

  @PostMapping(path = "load-vectors")
  public String loadVectors(Authentication authentication) throws IOException {

    Tenant tenant = tenantService.resolveForUsername(authentication.getName());

    vectorStoreService.loadDocuments(tenant);

    return "Documents loaded for tenant: " + tenant.getSlug();
  }

  @GetMapping("/rag")
  public String rag(@RequestParam String conversationId,
      @RequestParam @Size(max = PublicChatController.MAX_QUESTION_LENGTH) String question,
      Authentication authentication) {
    Tenant tenant = tenantService.resolveForUsername(authentication.getName());

    Object response = assistantService.chat(conversationId, question, tenant);

    if (response instanceof AssistantResponse assistantResponse) {
      return formatResponse(assistantResponse);
    }

    return response.toString();
  }

  private String formatResponse(AssistantResponse response) {

    if (response.type() == AssistantResponse.ResponseType.CLARIFICATION) {
      return response.message();
    }

    if (response.type() == AssistantResponse.ResponseType.APPROVAL_REQUIRED
        && response.draft() != null) {

      return """
          %s
          
          **Summary:** %s
          
          **Description:**
          %s
          """.formatted(response.message(), response.draft().summary(),
          response.draft().description());
    }

    return response.message();
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken csrfToken) {
    return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
  }
}