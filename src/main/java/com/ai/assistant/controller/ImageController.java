package com.ai.assistant.controller;

import com.ai.assistant.service.ImageAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final ImageAnalysisService imageAnalysisService;

    @PostMapping(
        value = "/analyze",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> analyzeImage(
        @RequestPart("image") MultipartFile image,
        @RequestParam(required = false) String question) {

        if (image.isEmpty()) {
            return ResponseEntity.badRequest()
                .body("Please upload an image.");
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest()
                .body("Image size must not exceed 5 MB.");
        }

        String contentType = image.getContentType();

        if (!MediaType.IMAGE_PNG_VALUE.equals(contentType)
            && !MediaType.IMAGE_JPEG_VALUE.equals(contentType)) {

            return ResponseEntity.badRequest()
                .body("Only PNG and JPEG images are supported.");
        }

        try {
            String result = imageAnalysisService.analyzeImage(
                image.getBytes(),
                contentType,
                question
            );

            return ResponseEntity.ok(result);

        } catch (Exception ex) {
            log.error("Image analysis failed", ex);

            return ResponseEntity.internalServerError()
                .body("Could not analyze the image. Please try again.");
        }
    }
}