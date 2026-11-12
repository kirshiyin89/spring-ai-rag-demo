package com.ai.assistant.controller;

import com.ai.assistant.service.AssistantResponse;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.SpeechToTextService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final SpeechToTextService speechToTextService;

    @PostMapping(
        value = "/transcribe",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public String transcribe(
            @RequestPart("audio") MultipartFile audio) {

        return speechToTextService.transcribe(audio);
    }


    private final AssistantService assistantService;


    @PostMapping(
        value = "/chat",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public VoiceChatResponse chat(
        @RequestParam String conversationId,
        @RequestPart("audio") MultipartFile audio) {

        String question = speechToTextService.transcribe(audio);

        Object result = assistantService.chat(conversationId, question);

        String answer = result instanceof AssistantResponse assistantResponse
            ? formatResponse(assistantResponse)
            : String.valueOf(result);

        return new VoiceChatResponse(question, answer);
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

    public record VoiceChatResponse(String question, String answer) {
    }


}
