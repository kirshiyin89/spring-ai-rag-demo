package com.ai.assistant.controller;

import com.ai.assistant.service.TicketStatusChangeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets/changes")
@RequiredArgsConstructor
public class TicketApprovalController {

    private final TicketStatusChangeService statusChangeService;

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<String> approveChange(
            @PathVariable UUID requestId,
            Authentication authentication) {

        String result =
            statusChangeService.approveChange(requestId, authentication);

        if (result.startsWith("APPROVED:")) {
            return ResponseEntity.ok(result);
        }

        if (result.startsWith("ACCESS_DENIED:")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(result);
        }

        if (result.startsWith("NOT_FOUND:")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(result);
        }

        if (result.startsWith("INVALID_STATE:")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(result);
        }

        if (result.startsWith("UPDATE_FAILED:")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(result);
        }

        return ResponseEntity.badRequest().body(result);
    }
}
