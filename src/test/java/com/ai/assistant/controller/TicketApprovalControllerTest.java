
package com.ai.assistant.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.ai.assistant.service.TicketStatusChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.ResponseEntity;

class TicketApprovalControllerTest {

  private TicketStatusChangeService statusChangeService;
  private TicketApprovalController controller;

  @BeforeEach
  void setUp() {
    statusChangeService = mock(TicketStatusChangeService.class);
    controller = new TicketApprovalController(statusChangeService);
  }

  @Test
  void shouldReturnOkWhenApprovalSucceeds() {
    UUID requestId = UUID.randomUUID();
    var admin = authentication("ROLE_IT_ADMIN");

    when(statusChangeService.approveChange(requestId, admin))
        .thenReturn("APPROVED: Ticket IT-1042 is now CLOSED.");

    ResponseEntity<String> response =
        controller.approveChange(requestId, admin);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(statusChangeService).approveChange(requestId, admin);
  }

  @Test
  void shouldReturnForbiddenWhenApprovalIsDenied() {
    UUID requestId = UUID.randomUUID();
    var employee = authentication("ROLE_EMPLOYEE");

    when(statusChangeService.approveChange(requestId, employee))
        .thenReturn("ACCESS_DENIED: Only IT administrators can approve changes.");

    ResponseEntity<String> response =
        controller.approveChange(requestId, employee);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
  }

  @Test
  void shouldReturnNotFoundForUnknownRequest() {
    UUID requestId = UUID.randomUUID();
    var admin = authentication("ROLE_IT_ADMIN");

    when(statusChangeService.approveChange(requestId, admin))
        .thenReturn("NOT_FOUND: Approval request does not exist.");

    ResponseEntity<String> response =
        controller.approveChange(requestId, admin);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  void shouldReturnConflictWhenRequestWasAlreadyProcessed() {
    UUID requestId = UUID.randomUUID();
    var admin = authentication("ROLE_IT_ADMIN");

    when(statusChangeService.approveChange(requestId, admin))
        .thenReturn("INVALID_STATE: This request has already been processed.");

    ResponseEntity<String> response =
        controller.approveChange(requestId, admin);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
  }

  private UsernamePasswordAuthenticationToken authentication(String role) {
    return new UsernamePasswordAuthenticationToken(
        "alice",
        "password",
        java.util.List.of(new SimpleGrantedAuthority(role))
    );
  }
}
