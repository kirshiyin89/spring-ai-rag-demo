package com.ai.assistant.controller;

import com.ai.assistant.service.AssistantResponse;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.VectorStoreService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/load-vectors")
    public String loadVectors() throws IOException {
        vectorStoreService.loadDocuments();
        return "done";
    }

    /**
     * Handles regular chat, investigation follow-ups, and ticket-draft replies
     * through one conversation-aware entry point.
     */
    @GetMapping("/rag")
    public String chat(
        @RequestParam String conversationId,
        @RequestParam String question
    ) {
        Object response = assistantService.chat(conversationId, question);
        return response instanceof AssistantResponse assistantResponse
            ? formatResponse(assistantResponse)
            : String.valueOf(response);
    }

    private String formatResponse(AssistantResponse response) {
        if (response.type() != AssistantResponse.ResponseType.APPROVAL_REQUIRED
            || response.draft() == null) {
            return response.message();
        }

        return """
            %s

            **Summary:** %s

            **Description:**
            %s
            """.formatted(
                response.message(),
                response.draft().summary(),
                response.draft().description()
            );
    }
}
