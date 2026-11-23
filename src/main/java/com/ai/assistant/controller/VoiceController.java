package com.ai.assistant.controller;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantResponse;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.SpeechToTextService;
import com.ai.assistant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final SpeechToTextService speechToTextService;
    private final TenantService tenantService;

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
        @RequestPart("audio") MultipartFile audio,
        Authentication authentication
    ) {
        String question = speechToTextService.transcribe(audio);
        if (question.length() > PublicChatController.MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Question must be at most " + PublicChatController.MAX_QUESTION_LENGTH + " characters");
        }

        Tenant tenant = tenantService.resolveForUsername(
            authentication.getName()
        );

        Object result = assistantService.chat(
            conversationId,
            question,
            tenant
        );

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
