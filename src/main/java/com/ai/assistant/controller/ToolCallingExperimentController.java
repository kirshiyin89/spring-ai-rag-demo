package com.ai.assistant.controller;

import com.ai.assistant.service.ToolCallingExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ToolCallingExperimentController {

    private final ToolCallingExperimentService experimentService;

    @GetMapping("/api/chat/tool-experiment")
    public String ask(
            @RequestParam String question) {
        return experimentService.ask(question);
    }
}