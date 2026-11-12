package com.ai.assistant.investigation;

import com.ai.assistant.service.YouTrackMcpGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvestigationIssueServiceImpl
    implements InvestigationIssueService {

  private final YouTrackMcpGateway youTrackMcpGateway;
  private final ObjectMapper objectMapper;

  @Override
  public List<String> searchRelatedIssues(List<String> queries) {

    if (queries == null || queries.isEmpty()) {
      return List.of();
    }

    Map<String, String> findings = new LinkedHashMap<>();

    log.info("Searching YouTrack with {} prepared queries", queries.size());

    for (String query : queries) {

      if (query == null || query.isBlank()) {
        continue;
      }

      log.info("Searching YouTrack with query: {}", query);

      String response = youTrackMcpGateway.searchIssues(query, 10, 0);

      log.debug("YouTrack search response for query {}: {}", query, response);

      int previousFindingCount = findings.size();

      addFindings(response, findings);

      log.info(
          "Parsed YouTrack query results. query={}, newUniqueFindings={}",
          query,
          findings.size() - previousFindingCount
      );
    }

    log.info(
        "Completed YouTrack issue search. uniqueFindingCount={}",
        findings.size()
    );

    return new ArrayList<>(findings.values());
  }

  private void addFindings(
      String response,
      Map<String, String> findings) {

    try {
      // Parse the outer MCP response.
      JsonNode mcpResponse = objectMapper.readTree(response);

      if (!mcpResponse.isArray() || mcpResponse.isEmpty()) {
        return;
      }

      // Extract the text containing the actual issue JSON.
      String issueJson = mcpResponse.get(0)
          .path("text")
          .asString();

      if (issueJson.isBlank()) {
        return;
      }

      // Parse the inner JSON array.
      JsonNode issues = objectMapper.readTree(issueJson);

      if (!issues.isArray()) {
        return;
      }

      for (JsonNode issue : issues) {

        String issueId = issue.path("idReadable").asString();

        if (issueId.isBlank()) {
          continue;
        }

        String finding = formatIssue(issue);

        // Deduplicate by issue ID, not by the entire description.
        findings.putIfAbsent(issueId, finding);
      }

    } catch (Exception ex) {
      throw new IllegalStateException(
          "Could not parse YouTrack search results", ex);
    }
  }

  private String formatIssue(JsonNode issue) {

    StringBuilder result = new StringBuilder();

    result.append("Issue ID: ")
        .append(issue.path("idReadable").asString())
        .append("\n");

    result.append("Summary: ")
        .append(issue.path("summary").asString())
        .append("\n");

    result.append("Description: ")
        .append(issue.path("description").asString())
        .append("\n");

    Map<String, String> fields = extractCustomFields(
        issue.path("customFields")
    );

    appendField(result, "Priority", fields.get("Priority"));
    appendField(result, "Type", fields.get("Type"));
    appendField(result, "State", fields.get("State"));
    appendField(result, "Assignee", fields.get("Assignee"));
    appendField(result, "Subsystem", fields.get("Subsystem"));

    return result.toString();
  }

  private Map<String, String> extractCustomFields(JsonNode customFields) {

    Map<String, String> fields = new LinkedHashMap<>();

    if (!customFields.isArray()) {
      return fields;
    }

    for (JsonNode field : customFields) {

      String name = field.path("name").asString();

      if (name.isBlank()) {
        continue;
      }

      JsonNode value = field.path("value");

      if (value.isMissingNode() || value.isNull()) {
        continue;
      }

      String fieldValue;

      if (value.isObject()) {
        fieldValue = value.path("name").asString();
      } else {
        fieldValue = value.asString();
      }

      if (!fieldValue.isBlank()) {
        fields.put(name, fieldValue);
      }
    }

    return fields;
  }

  private void appendField(
      StringBuilder result,
      String name,
      String value) {

    if (value != null && !value.isBlank()) {
      result.append(name)
          .append(": ")
          .append(value)
          .append("\n");
    }
  }
}
