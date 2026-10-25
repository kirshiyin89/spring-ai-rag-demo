package com.ai.assistant.controller;

import com.ai.assistant.service.YouTrackMcpGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class YouTrackPromptController {

    private final YouTrackMcpGateway gateway;

    public YouTrackPromptController(YouTrackMcpGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/api/youtrack/review-prompt")
    public String getReviewPrompt(
            @RequestParam String issueId) {
        return gateway.getIssueReviewPrompt(issueId);
    }
}
