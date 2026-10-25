package com.ai.assistant.controller;

import com.ai.assistant.service.YouTrackMcpGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class YouTrackResourceController {

    private final YouTrackMcpGateway youTrackMcpGateway;

    public YouTrackResourceController(
            YouTrackMcpGateway youTrackMcpGateway) {
        this.youTrackMcpGateway = youTrackMcpGateway;
    }

    @GetMapping("/api/youtrack/workflow")
    public String getWorkflow() {
        return youTrackMcpGateway.readWorkflowResource();
    }
}
