package com.ai.assistant.controller;

import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.service.AssistantService;
import com.ai.assistant.service.ImageAnalysisService;
import com.ai.assistant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

  private final ImageAnalysisService imageAnalysisService;

  private final AssistantService assistantService;

  private final TenantService tenantService;


  @PostMapping(
      value = "/analyze",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ResponseEntity<String> analyzeImage(
      @RequestParam String conversationId,
      @RequestPart("image") MultipartFile image,
      @RequestParam(required = false) String question,
      Authentication authentication
  ) {

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

    Tenant tenant = tenantService.resolveForUsername(
        authentication.getName()
    );

    try {
      String analysis = imageAnalysisService.analyzeImage(
          image.getBytes(),
          contentType,
          question
      );

      assistantService.rememberImageAnalysis(
          conversationId,
          question,
          analysis,
          tenant
      );

      return ResponseEntity.ok(analysis);

    } catch (Exception ex) {
      log.error("Image analysis failed", ex);

      return ResponseEntity.internalServerError()
          .body("Could not analyze the image. Please try again.");
    }
  }
}