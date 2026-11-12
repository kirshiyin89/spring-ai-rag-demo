package com.ai.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class SpeechToTextService {

    private static final String GROQ_TRANSCRIPTION_URL =
        "https://api.groq.com/openai/v1/audio/transcriptions";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public SpeechToTextService(
            RestClient.Builder restClientBuilder,
            @Value("${app.groq.api-key}") String apiKey,
            @Value("${app.voice.transcription.model:whisper-large-v3-turbo}")
            String model) {

        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public String transcribe(MultipartFile audioFile) {

        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException(
                "Audio file cannot be empty."
            );
        }

        log.info("Starting audio transcription. Model: {}", model);

        var multipartBody = new LinkedMultiValueMap<String, Object>();


        var fileResource = new org.springframework.core.io.ByteArrayResource(
            getAudioBytes(audioFile)
        ) {
            @Override
            public String getFilename() {
                return audioFile.getOriginalFilename() != null
                    ? audioFile.getOriginalFilename()
                    : "recording.webm";
            }
        };

        multipartBody.add("file", fileResource);
        multipartBody.add("model", model);
        multipartBody.add("response_format", "json");
        multipartBody.add("temperature", "0");

        try {
            GroqTranscriptionResponse response = restClient.post()
                .uri(GROQ_TRANSCRIPTION_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody)
                .retrieve()
                .body(GroqTranscriptionResponse.class);

            if (response == null || response.text() == null
                    || response.text().isBlank()) {
                throw new IllegalStateException(
                    "Groq returned an empty transcription."
                );
            }

            log.info("Audio transcription completed.");

            return response.text().trim();

        } catch (Exception ex) {
            log.error("Audio transcription failed.", ex);
            throw new IllegalStateException(
                "Could not transcribe the audio.", ex
            );
        }
    }

    private byte[] getAudioBytes(MultipartFile audioFile) {
        try {
            return audioFile.getBytes();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(
                "Could not read uploaded audio.", ex
            );
        }
    }

    private record GroqTranscriptionResponse(String text) {
    }
}
