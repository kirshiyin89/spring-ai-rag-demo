package com.ai.assistant.controller;

import com.ai.assistant.service.YouTrackIssueReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class YouTrackIssueReviewController {

    private final YouTrackIssueReviewService reviewService;

    public YouTrackIssueReviewController(
            YouTrackIssueReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/youtrack/review")
    public String reviewIssue(
            @RequestParam String issueId) {
        return reviewService.reviewIssue(issueId);
    }
}
