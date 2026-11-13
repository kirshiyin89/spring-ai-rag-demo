package com.ai.assistant.controller;

import com.ai.assistant.service.AssistantResponse;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.VectorStoreService;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
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


    @PostMapping(path = "load-vectors")
    public String loadVectors() throws IOException {
        vectorStoreService.loadDocuments();
        return "done";
    }

    @GetMapping("/rag")
    public String rag(
        @RequestParam String conversationId,
        @RequestParam String question
    ) {
        Object response = assistantService.chat(conversationId, question);

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
            """.formatted(
                response.message(),
                response.draft().summary(),
                response.draft().description()
            );
        }

        return response.message();
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
            "token", csrfToken.getToken(),
            "headerName", csrfToken.getHeaderName()
        );
    }
}