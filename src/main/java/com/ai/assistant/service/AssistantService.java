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

  public AssistantService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
      @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
      QueryCategoryService queryCategoryService,
      CompressionQueryTransformer compressionQueryTransformer,
      RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, TicketTools ticketTools,
      TicketSourceRouter ticketSourceRouter, YouTrackMcpGateway youTrackMcpGateway) {

    this.chatMemory = chatMemory;
    this.queryCategoryService = queryCategoryService;

    this.clarificationClient = chatClientBuilder.clone().build();
    this.compressionQueryTransformer = compressionQueryTransformer;
    this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
    this.ticketTools = ticketTools;
    this.ticketSourceRouter = ticketSourceRouter;
    this.youTrackMcpGateway = youTrackMcpGateway;

    this.chatClient = chatClientBuilder.defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()).build();

    this.systemPromptResource = systemPromptResource;
  }



  public String askWithRag(String conversationId, String question) {
    List<Message> history = chatMemory.get(conversationId);

    log.info("History messages: {}", history.size());
    log.info("Original question: {}", question);

    // Retrieve ticket context without returning early.
    String ticketContext = retrieveTicketContext(question);

    String standaloneQuestion = question;

    if (!history.isEmpty()) {
      Query queryWithHistory = Query.builder()
          .text(question)
          .history(history)
          .build();

      standaloneQuestion =
          compressionQueryTransformer.transform(queryWithHistory).text();
    }

    String category = queryCategoryService.classify(standaloneQuestion);
    log.info("Classified category: {}", category);

    // Preserve clarification for ordinary questions, but don't
    // discard a question that already contains a ticket reference.
    if ("CLARIFY".equalsIgnoreCase(category)
        && ticketContext.isBlank()) {
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

    return chatClient.prompt()
        .system(systemPrompt)
        .user(userMessage)
        .tools(ticketTools)
        .advisors(retrievalAugmentationAdvisor)
        .advisors(advisorSpec ->
            advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
  }

  private String retrieveTicketContext(String question) {
    Pattern pattern = Pattern.compile(
        "\\b([A-Z]+-\\d+)\\b",
        Pattern.CASE_INSENSITIVE);

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
        case POSTGRES ->
            ticketTools.getTicketStatus(ticketRoute.ticketId());

        case YOUTRACK ->
            askYouTrack(ticketRoute.ticketId());

        case UNKNOWN ->
            "Unsupported ticket prefix for " + ticketRoute.ticketId();
      };

      context.append("- ")
          .append(ticketId)
          .append(": ")
          .append(result)
          .append("\n");
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
          throw new IllegalArgumentException(
              "MCP response did not contain a text payload");
        }

        var response = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(json);

        if (response == null || !response.isObject()) {
          throw new IllegalArgumentException(
              "MCP response payload was not a JSON object");
        }

        // Handle a missing ticket before reading issue fields.
        String statusCode = response.path("status").asText();

        if ("NOT_FOUND".equals(statusCode)) {
          return response.path("message")
              .asText("YouTrack issue " + ticketId + " was not found.");
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
        String diagnosticResponse = result == null
            ? "<null>"
            : result.substring(0, Math.min(result.length(), 2000));

        log.warn(
            "Could not parse YouTrack MCP response for ticket {}. "
                + "Response (truncated to 2000 characters): {}",
            ticketId,
            diagnosticResponse,
            e
        );

        return "The YouTrack response could not be processed. "
            + "Please try again later.";
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
}