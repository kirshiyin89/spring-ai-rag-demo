package com.ai.assistant.service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import java.util.List;

@Slf4j
@Service
public class AssistantService {

  private final ChatClient chatClient;
  private final ChatClient clarificationClient;
  private final ChatMemory chatMemory;
  private final Resource systemPromptResource;
  private final QueryCategoryService queryCategoryService;
  private final CompressionQueryTransformer compressionQueryTransformer;
  private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
  private final TicketTools ticketTools;
  private final TicketSourceRouter ticketSourceRouter;
  private final YouTrackMcpGateway youTrackMcpGateway;
  private final TicketAuthorizationService ticketAuthorizationService;
  private final PendingIssueDraftStore pendingDraftStore;

  public AssistantService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
      @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
      QueryCategoryService queryCategoryService,
      CompressionQueryTransformer compressionQueryTransformer,
      RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, TicketTools ticketTools,
      TicketSourceRouter ticketSourceRouter, YouTrackMcpGateway youTrackMcpGateway,
      TicketAuthorizationService ticketAuthorizationService,
      PendingIssueDraftStore pendingDraftStore) {

    this.chatMemory = chatMemory;
    this.queryCategoryService = queryCategoryService;

    this.clarificationClient = chatClientBuilder.clone().build();
    this.compressionQueryTransformer = compressionQueryTransformer;
    this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
    this.ticketTools = ticketTools;
    this.ticketSourceRouter = ticketSourceRouter;
    this.youTrackMcpGateway = youTrackMcpGateway;
    this.ticketAuthorizationService = ticketAuthorizationService;
    this.pendingDraftStore = pendingDraftStore;

    this.chatClient = chatClientBuilder.defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()).build();

    this.systemPromptResource = systemPromptResource;
  }


  public String askWithRag(String conversationId, String question) {
    List<Message> history = chatMemory.get(conversationId);

    log.info("History messages: {}", history.size());
    log.info("Original question: {}", question);

    // Check authorization before retrieving ticket information.
    if (ticketSourceRouter.route(question).isPresent()) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

      if (!ticketAuthorizationService.canReadTickets(authentication)) {
        return "You are not authorized to access ticket information.";
      }
    }

    String ticketContext = retrieveTicketContext(question);

    String standaloneQuestion = question;

    if (!history.isEmpty()) {
      Query queryWithHistory = Query.builder().text(question).history(history).build();

      standaloneQuestion = compressionQueryTransformer.transform(queryWithHistory).text();
    }

    String category = queryCategoryService.classify(standaloneQuestion);
    log.info("Classified category: {}", category);

    if ("CLARIFY".equalsIgnoreCase(category) && ticketContext.isBlank()) {
      return generateClarification(question);
    }

    String systemPrompt = new PromptTemplate(systemPromptResource).render();

    String userMessage = standaloneQuestion;

    if (!ticketContext.isBlank()) {
      userMessage += """
          
          Retrieved ticket information:
          Use the following results as factual context.
          If a ticket lookup failed, report that clearly.
          Do not invent ticket details.
          
          """ + ticketContext;
    }

    var promptSpec = chatClient.prompt().system(systemPrompt).user(userMessage);

// Ticket IDs have already been handled by Java routing.
// Don't let the model attempt a second lookup using the wrong source.
    if (ticketSourceRouter.route(question).isEmpty()) {
      promptSpec.tools(ticketTools);
    }

    return promptSpec.advisors(retrievalAugmentationAdvisor)
        .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call().content();
  }

  private String retrieveTicketContext(String question) {
    Pattern pattern = Pattern.compile("\\b([A-Z]+-\\d+)\\b", Pattern.CASE_INSENSITIVE);

    Matcher matcher = pattern.matcher(question);
    Set<String> ticketIds = new LinkedHashSet<>();

    while (matcher.find()) {
      ticketIds.add(matcher.group(1).toUpperCase(java.util.Locale.ROOT));
    }

    if (ticketIds.isEmpty()) {
      return "";
    }

    StringBuilder context = new StringBuilder();

    for (String ticketId : ticketIds) {
      var route = ticketSourceRouter.route(ticketId);

      if (route.isEmpty()) {
        continue;
      }

      var ticketRoute = route.get();

      String result = switch (ticketRoute.source()) {
        case POSTGRES -> ticketTools.getTicketStatus(ticketRoute.ticketId());

        case YOUTRACK -> askYouTrack(ticketRoute.ticketId());

        case UNKNOWN -> "Unsupported ticket prefix for " + ticketRoute.ticketId();
      };

      context.append("- ").append(ticketId).append(": ").append(result).append("\n");
    }

    return context.toString();
  }


  private String askYouTrack(String ticketId) {
    try {
      String result = youTrackMcpGateway.getIssue(ticketId);

      try {
        var outer = new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, String>>>() {
        };

        var entries = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, outer);

        if (entries.isEmpty()) {
          return "No YouTrack issue details were returned.";
        }

        String json = entries.getFirst().get("text");

        if (json == null || json.isBlank()) {
          throw new IllegalArgumentException("MCP response did not contain a text payload");
        }

        var response = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);

        if (response == null || !response.isObject()) {
          throw new IllegalArgumentException("MCP response payload was not a JSON object");
        }

        // Handle a missing ticket before reading issue fields.
        String statusCode = response.path("status").asText();

        if ("NOT_FOUND".equals(statusCode)) {
          return response.path("message").asText("YouTrack issue " + ticketId + " was not found.");
        }

        if ("AUTHENTICATION_FAILED".equals(statusCode)) {
          log.error("YouTrack authentication or authorization failed.");
          return "The YouTrack integration is not authorized. Please contact the administrator.";
        }

        if (!"FOUND".equals(statusCode)) {
          log.error("Unexpected YouTrack response status: {}", statusCode);
          return "Could not process the YouTrack response. Please try again later.";
        }

        var issue = response.path("issue");

        log.info("YouTrack MCP lookup completed for {}", ticketId);

        String status = "Unknown";

        var customFields = issue.path("customFields");

        if (customFields.isArray()) {
          for (var field : customFields) {
            if ("State".equals(field.path("name").toString().replace("\"", ""))) {

              var value = field.path("value");
              var nameNode = value.path("name");

              if (!nameNode.isMissingNode() && !nameNode.isNull()) {
                status = nameNode.toString().replace("\"", "");
              }
              break;
            }
          }
        }

        return "YouTrack issue " + issue.path("idReadable").asText() + ": " + issue.path("summary")
            .asText() + " — Status: " + status;


      } catch (Exception e) {
        String diagnosticResponse =
            result == null ? "<null>" : result.substring(0, Math.min(result.length(), 2000));

        log.warn("Could not parse YouTrack MCP response for ticket {}. "
            + "Response (truncated to 2000 characters): {}", ticketId, diagnosticResponse, e);

        return "The YouTrack response could not be processed. " + "Please try again later.";
      }


    } catch (YouTrackMcpGateway.YouTrackUnavailableException e) {
      log.warn("YouTrack is unavailable; lookup skipped for {}", ticketId);
      return "YouTrack is temporarily unavailable. Please try again later.";
    }
  }


  private String generateClarification(String question) {
    return Objects.requireNonNull(clarificationClient.prompt().system("""
        You are a friendly assistant for FinFlow Technologies,
        helping employees with internal company documentation.
        
        The employee's request is ambiguous or lacks enough
        detail to identify what they need.
        
        Ask one short, natural follow-up question that helps
        identify their intent.
        
        Use the employee's original wording and context.
        Offer relevant examples from these topics when useful:
        onboarding, GitLab access, VPN and WLAN, hardware
        requests, software licenses, and company information.
        
        Do not answer the original question.
        Do not invent company policies or procedures.
        Do not ask multiple questions at once.
        """).user(question).call().content()).trim();
  }


  public AssistantResponse prepareIssueDraft(String conversationId, String question) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "Please sign in before preparing a support issue.", null, null);
    }

    String username = authentication.getName();

    String systemPrompt = """
        You are FinFlow's internal IT support assistant.
        
        Investigate the employee's problem using retrieved internal
        documentation and the employee's reported information.
        
        If essential information is missing, return CLARIFICATION.
        
        If sufficient information exists, return ISSUE_DRAFT.
        
        For CLARIFICATION:
        - Include a concise message asking for missing information.
        - Set draft to null.
        - Set draftId to null.
        
        For ISSUE_DRAFT:
        - Include a concise summary.
        - Include a complete description.
        - Set draftId to null.
        - Do not generate your own draft ID.
        
        Never return DRAFT as the response type.
        Never invent employee information or technical details.
        Never create a YouTrack issue.
        Never claim that an issue has been created.
        
        Java manages draft IDs, pending drafts, and approval.
        
        Return the response using the AssistantResponse structure.
        """;

    AssistantResponse generated;

    try {
      generated = chatClient.prompt().system(systemPrompt).user(question)
          .advisors(retrievalAugmentationAdvisor)
          .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
          .call().entity(AssistantResponse.class);

    } catch (Exception ex) {
      log.error("Issue draft generation failed", ex);

      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "I couldn't investigate the issue right now. Please try again.", null, null);
    }

    if (generated == null || generated.type() == null) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "I couldn't process the investigation response. Please try again.", null, null);
    }

    if (generated.type() == AssistantResponse.ResponseType.CLARIFICATION) {
      return new AssistantResponse(AssistantResponse.ResponseType.CLARIFICATION,
          generated.message(), null, null);
    }

    if (generated.type() != AssistantResponse.ResponseType.ISSUE_DRAFT || generated.draft() == null
        || generated.draft().summary() == null || generated.draft().summary().isBlank()
        || generated.draft().description() == null || generated.draft().description().isBlank()) {

      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          generated.message() == null ? "I need more information before preparing an issue."
              : generated.message(), null, null);
    }

    var pending = pendingDraftStore.save(username, conversationId, generated.draft());

    return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED,
        "Review the proposed issue below. No ticket has been created. " + "Draft ID: "
            + pending.draftId(), pending.draftId(), pending.draft());
  }


  public AssistantResponse approveIssueDraft(String conversationId, String draftId) {
    String username = requireCurrentUsername();

    var claimed = pendingDraftStore.claim(username, conversationId, draftId);

    if (claimed.isEmpty()) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "This draft is missing, expired, or already being processed. "
              + "Please prepare a new draft if needed.", null, null);
    }

    var pending = claimed.get();

    try {
      var createdIssue = youTrackMcpGateway.createIssue(pending.draft().summary(),
          pending.draft().description());

      pendingDraftStore.complete(username, conversationId, draftId);

      return new AssistantResponse(AssistantResponse.ResponseType.ISSUE_CREATED,
          "Your YouTrack issue was created successfully: " + "[" + createdIssue.idReadable() + "]("
              + createdIssue.url() + ")", null, null);

    } catch (Exception ex) {
      log.error("Issue creation failed for draft {}", draftId, ex);

      return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED,
          "Issue creation failed or its outcome is uncertain. "
              + "Check YouTrack before trying again to avoid " + "creating a duplicate.", draftId,
          pending.draft());
    }
  }


  public AssistantResponse cancelIssueDraft(String conversationId, String draftId) {
    String username = requireCurrentUsername();

    var pending = pendingDraftStore.get(username, conversationId);

    if (pending.isEmpty() || !pending.get().draftId().equals(draftId)
        || pending.get().status() != PendingIssueDraftStore.Status.PENDING) {

      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The draft is missing or already being processed.", null, null);
    }

    boolean cancelled = pendingDraftStore.cancel(username, conversationId, draftId);

    if (!cancelled) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The draft could not be cancelled because its state changed.", draftId,
          pending.get().draft());
    }

    return new AssistantResponse(AssistantResponse.ResponseType.CANCELLED,
        "The ticket draft was cancelled. No issue was created.", null, null);
  }


  public AssistantResponse editIssueDraft(String conversationId, String draftId,
      AssistantResponse.IssueDraft updatedDraft) {
    String username = requireCurrentUsername();

    if (updatedDraft == null || updatedDraft.summary() == null || updatedDraft.summary().isBlank()
        || updatedDraft.description() == null || updatedDraft.description().isBlank()) {

      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The issue summary and description cannot be blank.", null, null);
    }

    var updated = pendingDraftStore.update(username, conversationId, draftId, updatedDraft);

    if (!updated) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The draft is missing or already being processed.", null, null);
    }

    return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED,
        "Draft updated. Please review it before approving.", draftId, updatedDraft);
  }


  private String requireCurrentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authentication required");
    }

    return authentication.getName();
  }

  public Object chat(String conversationId, String question) {

    String username = requireCurrentUsername();

    log.info(
        "Checking pending draft: username={}, conversationId={}",
        username,
        conversationId
    );

    var pending = pendingDraftStore.get(username, conversationId);

    log.info("Chat request: conversationId={}, question={}", conversationId, question);

    log.info("Pending draft exists: {}", pending.isPresent());

    // Existing draft always takes priority.
    if (pending.isPresent()) {

      var draft = pending.get();

      if (draft.status() == PendingIssueDraftStore.Status.CREATING) {
        return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED,
            "The ticket is currently being created. " + "Please check YouTrack before retrying.",
            draft.draftId(), draft.draft());
      }

      // Handle unambiguous approvals without another LLM call.
      if (isExplicitApproval(question)) {
        return approveIssueDraft(conversationId, draft.draftId());
      }

      var action = interpretDraftResponse(
          question,
          draft.draft()
      );

      return switch (action.action()) {

        case APPROVE -> approveIssueDraft(conversationId, draft.draftId());

        case CANCEL -> cancelIssueDraft(conversationId, draft.draftId());

        case EDIT -> {

          var updatedDraft = action.updatedDraft();

          if (updatedDraft == null || updatedDraft.summary() == null || updatedDraft.summary()
              .isBlank() || updatedDraft.description() == null || updatedDraft.description()
              .isBlank()) {

            yield pendingDraftResponse(draft.draftId(), draft.draft(),
                "I couldn't update the draft. " + "Please describe the changes.");
          }

          yield editIssueDraft(conversationId, draft.draftId(), updatedDraft);
        }

        case ASK_QUESTION, KEEP_PENDING -> {

          String message = action.message();

          if (message == null || message.isBlank()) {
            message = "The draft is still waiting for your approval.";
          }

          yield pendingDraftResponse(draft.draftId(), draft.draft(), message);
        }
      };
    }

    // No pending draft: detect new ticket intent.
    if (isTicketRequest(question)) {
      log.info("Ticket request detected: {}", isTicketRequest(question));
      return prepareIssueDraft(conversationId, question);
    }

    // Preserve existing RAG and ticket-status functionality.
    return askWithRag(conversationId, question);
  }

  private boolean isExplicitApproval(String question) {

    String normalized = question.trim().toLowerCase();

    return normalized.matches("^(yes|yes please|approve|approved|create it|go ahead|"
        + "proceed|looks good|confirmed|confirm|do it)[.! ]*$");
  }

  private AssistantResponse pendingDraftResponse(String draftId, AssistantResponse.IssueDraft draft,
      String message) {

    return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED, message, draftId,
        draft);
  }

  private AssistantResponse.DraftActionResponse interpretDraftResponse(String question,
      AssistantResponse.IssueDraft currentDraft) {

    String systemPrompt = """
                You interpret an employee's reply to a pending YouTrack ticket draft.
        
                Determine the employee's intended action.
        
                Available actions:
        
        APPROVE:
        The employee gives clear consent to create the current draft.
        
        Recognize natural language confirmations, including:
        - Direct yes/no confirmations
        - Informal acknowledgments
        - Positive feedback followed by approval
        - Polite requests to proceed
        - Short conversational replies that clearly confirm
          the preceding draft
        
        Examples:
        - "Yes, create it" → APPROVE
        - "Okay, go ahead" → APPROVE
        - "Sounds good" → APPROVE
        - "Perfect, thanks" → APPROVE
        - "That's fine" → APPROVE
        - "Please do" → APPROVE
        - "Let's proceed" → APPROVE
        
        Interpret the employee's response in the context of
        the pending draft and the preceding approval request.
        
        Do not require specific keywords.
        
        Do not interpret questions, requested changes,
        or expressions of uncertainty as approval.
        
        When the employee's intent remains genuinely unclear,
        return KEEP_PENDING.
        
        Never treat unrelated messages as approval.
        
                CANCEL:
                The employee wants to cancel or discard the draft.
        
                EDIT:
                The employee requests changes to the draft.
        
                ASK_QUESTION:
                The employee asks a question about the draft.
        
                KEEP_PENDING:
                The employee's intent is unclear or they have not approved,
                cancelled, or requested changes.
        
                Rules:
                - Interpret the reply using the existing draft as context.
                - Understand natural language and conversational replies.
                - "Yes, create it" and "Looks good, go ahead" mean APPROVE.
                - "Actually, change the summary" means EDIT.
                - "Never mind" means CANCEL when it clearly refers to the draft.
                - Never interpret ambiguous responses as approval.
                - For EDIT, return the complete updated draft.
                - Preserve existing information unless the employee requests changes.
                - Never invent technical details.
                - Never create a ticket.
                - Java controls ticket creation and approval.
        
                Return a DraftActionResponse.
        """;

    try {

      return clarificationClient.prompt().system(systemPrompt).user("""
              Existing ticket draft:
              
              Summary:
              %s
              
              Description:
              %s
              
              Employee's reply:
              %s
              """.formatted(currentDraft.summary(), currentDraft.description(), question)).call()
          .entity(AssistantResponse.DraftActionResponse.class);

    } catch (Exception ex) {

      log.error("Could not interpret draft response", ex);

      return new AssistantResponse.DraftActionResponse(AssistantResponse.DraftAction.KEEP_PENDING,
          "I couldn't interpret your response. " + "The draft remains pending.", null);
    }
  }

  private boolean isTicketRequest(String question) {

    String result = clarificationClient.prompt().system("""
        Determine whether the user explicitly requests creating
        a YouTrack ticket or issue.
        
        Return exactly one word:
        TICKET
        CHAT
        
        TICKET means the user wants a new YouTrack issue created.
        CHAT means the user wants information, troubleshooting,
        or a normal conversation.
        
        Do not classify a question about an existing ticket
        as a request to create a new ticket.
        """).user(question).call().content();

    return result != null && result.trim().equalsIgnoreCase("TICKET");
  }

}