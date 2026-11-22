package com.ai.assistant.service;

import com.ai.assistant.persistence.Tenant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
public class AssistantService {

  private final ChatClient chatClient;
  private final ChatClient clarificationClient;
  private final ChatMemory chatMemory;
  private final QueryCategoryService queryCategoryService;
  private final CompressionQueryTransformer compressionQueryTransformer;
  private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
  private final TicketTools ticketTools;
  private final TicketSourceRouter ticketSourceRouter;
  private final YouTrackMcpGateway youTrackMcpGateway;
  private final TicketAuthorizationService ticketAuthorizationService;
  private final PendingIssueDraftStore pendingDraftStore;
  private final ConversationalIntentClassifier conversationalIntentClassifier;
  private final TenantService tenantService;

  public AssistantService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
      QueryCategoryService queryCategoryService,
      CompressionQueryTransformer compressionQueryTransformer,
      RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, TicketTools ticketTools,
      TicketSourceRouter ticketSourceRouter, YouTrackMcpGateway youTrackMcpGateway,
      TicketAuthorizationService ticketAuthorizationService,
      PendingIssueDraftStore pendingDraftStore,
      ConversationalIntentClassifier conversationalIntentClassifier, TenantService tenantService) {

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
    this.conversationalIntentClassifier = conversationalIntentClassifier;
    this.tenantService = tenantService;

    this.chatClient = chatClientBuilder.defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
  }


  public String askWithRag(String conversationId, String question) {
    String username = requireCurrentUsername();
    Tenant tenant = tenantService.resolveForUsername(username);

    return askWithRag(conversationId, question, tenant);
  }

  public String askWithRag(
      String conversationId,
      String question,
      Tenant tenant
  ) {
    String memoryConversationId =
        tenantScopedConversationId(conversationId, tenant);

    List<Message> history = chatMemory.get(memoryConversationId);
    log.info("History messages: {}", history.size());
    log.info("Original question: {}", question);

    // Ticket lookups are FinFlow-only. Other tenants never reach ticket data.
    boolean ticketsEnabled = ticketsEnabled(tenant);

    // Check authorization before retrieving ticket information.
    if (ticketsEnabled && ticketSourceRouter.route(question).isPresent()) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

      if (!ticketAuthorizationService.canReadTickets(authentication)) {
        return "You are not authorized to access ticket information.";
      }
    }

    String ticketContext = ticketsEnabled ? retrieveTicketContext(question) : "";

    String standaloneQuestion = question;

    if (!history.isEmpty()) {
      Query queryWithHistory = Query.builder().text(question).history(history).build();

      standaloneQuestion = compressionQueryTransformer.transform(queryWithHistory).text();
    }

    String category = queryCategoryService.classify(
        standaloneQuestion,
        tenant
    );
    log.info("Classified category: {}", category);

    if ("CLARIFY".equalsIgnoreCase(category) && ticketContext.isBlank()) {
      return generateClarification(question, tenant);
    }

    if ("NONE".equalsIgnoreCase(category)) {
      return switch (tenant.getSlug()) {
        case "brewly" ->
            "I'm here to help with coffee, brewing methods, coffee beans, "
                + "equipment, and our delivery, returns and orders. "
                + "I can't help with that topic.";

        case "finflow" ->
            "I can help with FinFlow company documentation, "
                + "employee onboarding, internal IT support, and YouTrack tickets. "
                + "I can't answer general knowledge questions.";

        default -> throw new IllegalArgumentException(
            "Unsupported tenant: " + tenant.getSlug()
        );
      };
    }

    // Each tenant's persona lives in tenants.system_prompt. Never fall back to another
    // tenant's prompt; a missing prompt is a configuration error.
    String systemPrompt = tenant.getSystemPrompt();

    if (systemPrompt == null || systemPrompt.isBlank()) {
      throw new IllegalStateException(
          "Tenant " + tenant.getSlug() + " has no system prompt configured"
      );
    }

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
    if (ticketsEnabled && ticketSourceRouter.route(question).isEmpty()) {
      promptSpec.tools(ticketTools);
    }

    var tenantFilter = new FilterExpressionBuilder()
        .eq("tenantId", tenant.getId())
        .build();

    return promptSpec
        .advisors(retrievalAugmentationAdvisor)
        .advisors(advisorSpec -> advisorSpec
            .param(ChatMemory.CONVERSATION_ID, memoryConversationId)
            .param(
                org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                tenantFilter
            ))
        .call()
        .content();
  }

  /**
   * Answers an anonymous public visitor. Greetings, thanks and farewells get a short
   * conversational reply. Everything else goes through the knowledge path. The draft
   * and ticket flows are never used here.
   */
  public String answerPublic(String conversationId, String question, Tenant tenant) {
    var conversationalIntent = conversationalIntentClassifier.classify(question);

    if (conversationalIntent.isPresent()) {
      return handleConversationalIntent(conversationalIntent.get()).message();
    }

    return askWithRag(conversationId, question, tenant);
  }

  private boolean ticketsEnabled(Tenant tenant) {
    return tenant != null && "finflow".equals(tenant.getSlug());
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


  private String generateClarification(String question, Tenant tenant) {
    if ("brewly".equals(tenant.getSlug())) {
      return generateBrewlyClarification(question);
    }

    return generateFinflowClarification(question);
  }

  private String generateBrewlyClarification(String question) {
    return Objects.requireNonNull(clarificationClient.prompt().system("""
        You are the friendly customer assistant for Brewly Coffee, a coffee roastery in Budapest.

        The customer's message is unclear or is missing a detail needed to answer it.

        Ask one short, warm follow-up question that helps you understand what they need.
        Use the customer's own words where possible.
        Where it helps, offer a few examples from these topics: coffee beans, espresso,
        filter coffee, brewing methods, equipment, delivery, returns and orders.

        Keep it to one or two short sentences in simple language.
        Do not answer the original question.
        Ask only one question.
        Do not invent prices, delivery times, policies or contact details.
        Do not mention employees, IT, tickets or internal systems.
        """).user(question).call().content()).trim();
  }

  private String generateFinflowClarification(String question) {
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


  public AssistantResponse prepareIssueDraft(String conversationId, String question,
      Tenant tenant) {
    requireFinflowTicketWorkflow(tenant);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "Please sign in before preparing a support issue.", null, null);
    }

    String username = authentication.getName();

    // Persist ticket intent before asking for clarification. A later reply
    // usually does not mention a ticket, so it must not be routed as general
    // chat.
    var collectingDraft = pendingDraftStore.startCollecting(tenant.getId(), username, conversationId, question);
    String systemPrompt = """
        You are FinFlow's internal IT support assistant.
        
        Prepare a ticket from the employee's reported information.
        
        Do not answer the employee with documentation or troubleshooting
        guidance. Your only output must be CLARIFICATION or ISSUE_DRAFT.
        Employee-reported details are sufficient for a basic support ticket
        unless an essential detail is genuinely missing.
        
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

    String memoryConversationId =
        tenantScopedConversationId(conversationId, tenant);

    String conversationContext = chatMemory.get(memoryConversationId).stream()
        .map(message -> message.getMessageType() + ": " + message.getText())
        .collect(java.util.stream.Collectors.joining("\n\n"));

    AssistantResponse generated;

    try {
      generated = clarificationClient.prompt()
          .system(systemPrompt)
          .user("""
              Previous conversation context, including screenshot analysis:
              
              %s
              
              Current employee message:
              %s
              
              Employee-reported details collected before this message:
              %s
              
              Use the previous conversation context when preparing the issue.
              Include relevant error messages from screenshot analysis.
              Clearly distinguish observed errors from possible causes.
              Do not ask the employee to repeat information already available.
              """.formatted(
              conversationContext,
              question,
              collectingDraft.draft().description()
          ))
          .call()
          .entity(AssistantResponse.class);
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

    var pending = pendingDraftStore.save(tenant.getId(), username, conversationId, generated.draft());

    return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED,
        "Review the proposed issue below. No ticket has been created. " + "Draft ID: "
            + pending.draftId(), pending.draftId(), pending.draft());
  }


  public AssistantResponse approveIssueDraft(String conversationId, String draftId, Tenant tenant) {
    requireFinflowTicketWorkflow(tenant);
    String username = requireCurrentUsername();

    var claimed = pendingDraftStore.claim(tenant.getId(), username, conversationId, draftId);

    if (claimed.isEmpty()) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "This draft is missing, expired, or already being processed. "
              + "Please prepare a new draft if needed.", null, null);
    }

    var pending = claimed.get();

    try {
      var createdIssue = youTrackMcpGateway.createIssue(pending.draft().summary(),
          pending.draft().description());

      pendingDraftStore.complete(tenant.getId(), username, conversationId, draftId);

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


  public AssistantResponse cancelIssueDraft(String conversationId, String draftId, Tenant tenant) {
    requireFinflowTicketWorkflow(tenant);
    String username = requireCurrentUsername();

    var pending = pendingDraftStore.get(tenant.getId(), username, conversationId);

    if (pending.isEmpty() || !pending.get().draftId().equals(draftId)
        || pending.get().status() != PendingIssueDraftStore.Status.PENDING) {

      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The draft is missing or already being processed.", null, null);
    }

    boolean cancelled = pendingDraftStore.cancel(tenant.getId(), username, conversationId, draftId);

    if (!cancelled) {
      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The draft could not be cancelled because its state changed.", draftId,
          pending.get().draft());
    }

    return new AssistantResponse(AssistantResponse.ResponseType.CANCELLED,
        "The ticket draft was cancelled. No issue was created.", null, null);
  }


  public AssistantResponse editIssueDraft(String conversationId, String draftId, Tenant tenant,
      AssistantResponse.IssueDraft updatedDraft) {
    String username = requireCurrentUsername();

    if (updatedDraft == null || updatedDraft.summary() == null || updatedDraft.summary().isBlank()
        || updatedDraft.description() == null || updatedDraft.description().isBlank()) {

      return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
          "The issue summary and description cannot be blank.", null, null);
    }

    var updated = pendingDraftStore.update(tenant.getId(), username, conversationId, draftId, updatedDraft);

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
    Tenant tenant = tenantService.resolveForUsername(
        requireCurrentUsername()
    );

    return chat(conversationId, question, tenant);
  }

  public Object chat(
      String conversationId,
      String question,
      Tenant tenant
  ) {

    String username = requireCurrentUsername();

    log.info(
        "Checking pending draft: username={}, conversationId={}",
        username,
        conversationId
    );

    var pending = pendingDraftStore.get(tenant.getId(), username, conversationId);

    log.info("Chat request: conversationId={}, question={}", conversationId, question);

    log.info("Pending draft exists: {}", pending.isPresent());

    // Existing draft always takes priority.
    if (pending.isPresent()) {

      var draft = pending.get();

      // The employee is answering a ticket clarification. Continue the draft
      // workflow directly rather than checking for new-ticket wording again.
      if (draft.status() == PendingIssueDraftStore.Status.COLLECTING) {
        if (isDraftStatusQuestion(question)) {
          return new AssistantResponse(AssistantResponse.ResponseType.CLARIFICATION,
              "No YouTrack ticket has been created yet. This saved draft is still collecting "
                  + "the information needed to prepare it. Please answer the outstanding "
                  + "clarification, or provide the missing issue details.", null, null);
        }

        return prepareIssueDraft(conversationId, question, tenant);
      }
      if (draft.status() == PendingIssueDraftStore.Status.CREATING) {
        return new AssistantResponse(AssistantResponse.ResponseType.APPROVAL_REQUIRED,
            "The ticket is currently being created. " + "Please check YouTrack before retrying.",
            draft.draftId(), draft.draft());
      }

      // Handle unambiguous approvals without another LLM call.
      if (isExplicitApproval(question)) {
        return approveIssueDraft(conversationId, draft.draftId(), tenant);
      }

      var action = interpretDraftResponse(
          question,
          draft.draft()
      );

      return switch (action.action()) {

        case APPROVE -> approveIssueDraft(conversationId, draft.draftId(), tenant);

        case CANCEL -> cancelIssueDraft(conversationId, draft.draftId(), tenant);

        case EDIT -> {

          var updatedDraft = action.updatedDraft();

          if (updatedDraft == null || updatedDraft.summary() == null || updatedDraft.summary()
              .isBlank() || updatedDraft.description() == null || updatedDraft.description()
              .isBlank()) {

            yield pendingDraftResponse(draft.draftId(), draft.draft(),
                "I couldn't update the draft. " + "Please describe the changes.");
          }

          yield editIssueDraft(conversationId, draft.draftId(), tenant, updatedDraft);
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

    // No pending draft: handle simple conversational messages.
    var conversationalIntent = conversationalIntentClassifier.classify(question);

    if (conversationalIntent.isPresent()) {
      return handleConversationalIntent(conversationalIntent.get());
    }

// Detect new ticket intent.
    if (isTicketRequest(question)) {
      if (!"finflow".equals(tenant.getSlug())) {
        return new AssistantResponse(AssistantResponse.ResponseType.ANSWER,
            "Ticket workflows are restricted to FinFlow users.", null, null);
      }
      log.info("Ticket request detected: {}", isTicketRequest(question));
      return prepareIssueDraft(conversationId, question, tenant);
    }

    // Preserve existing RAG and ticket-status functionality.
    return askWithRag(conversationId, question, tenant);
  }

  private boolean isDraftStatusQuestion(String question) {
    String normalized = question.toLowerCase(java.util.Locale.ROOT);

    return normalized.contains("status")
        && (normalized.contains("ticket") || normalized.contains("issue")
        || normalized.contains("draft") || normalized.contains("create"));
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

  private AssistantResponse handleConversationalIntent(
      ConversationalIntentClassifier.Intent intent) {

    String message = switch (intent) {
      case GREETING -> "Hello! How can I help you today?";

      case THANKS -> "You're welcome! Happy to help.";

      case FAREWELL -> "Goodbye! Have a great day.";

      case ACKNOWLEDGMENT -> "Got it! Let me know if you need anything else.";
    };

    return new AssistantResponse(
        AssistantResponse.ResponseType.ANSWER,
        message,
        null,
        null
    );
  }

  public void rememberImageAnalysis(
      String conversationId,
      String question,
      String analysis,
      Tenant tenant
  ) {
    String userMessage = (question == null || question.isBlank())
        ? "The employee uploaded a screenshot for analysis."
        : "The employee uploaded a screenshot and asked: " + question;

    String assistantMessage = """
        Screenshot analysis (AI-generated, not independently verified):
        
        %s
        """.formatted(analysis);

    String memoryConversationId =
        tenantScopedConversationId(conversationId, tenant);

    chatMemory.add(
        memoryConversationId,
        List.of(
            new UserMessage(userMessage),
            new AssistantMessage(assistantMessage)
        )
    );

    log.info("Image analysis added to conversation memory: {}", conversationId);
  }

  private void requireFinflowTicketWorkflow(Tenant tenant) {
    if (tenant == null || !"finflow".equals(tenant.getSlug())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Ticket workflows are restricted to FinFlow users.");
    }
  }

  private String tenantScopedConversationId(
      String conversationId,
      Tenant tenant
  ) {
    if (tenant == null || tenant.getId() == null) {
      throw new IllegalArgumentException(
          "A valid tenant is required for conversation memory"
      );
    }

    if (conversationId == null || conversationId.isBlank()) {
      throw new IllegalArgumentException(
          "Conversation ID cannot be empty"
      );
    }

    return "tenant-" + tenant.getId() + ":" + conversationId;
  }

}