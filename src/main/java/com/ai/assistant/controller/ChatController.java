package com.ai.assistant.controller;

import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.VectorStoreService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AssistantService assistantService;
    private final VectorStoreService vectorStoreService;


    @GetMapping(path = "load-vectors")
    public String loadVectors() throws IOException {
        vectorStoreService.loadDocuments();
        return "done";
    }

    @GetMapping("/rag")
    public String rag(
        @RequestParam String conversationId,
        @RequestParam String question,
        @RequestParam(required = false) String category) {

        return assistantService.askWithRag(
            conversationId,
            question,
            category
        );
    }
}