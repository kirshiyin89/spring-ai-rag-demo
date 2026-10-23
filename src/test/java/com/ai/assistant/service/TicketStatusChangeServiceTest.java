package com.ai.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;


class TicketStatusChangeServiceTest {

  private TicketStatusChangeService service;
  private TicketService ticketService;
  private TicketStatusChangeRepository repository;

  private final Map<UUID, PendingTicketChange> storedRequests =
      new HashMap<>();


  @BeforeEach
  void setUp() {
    ticketService = mock(TicketService.class);
    repository = mock(TicketStatusChangeRepository.class);

    storedRequests.clear();

    when(repository.findById(any(UUID.class)))
        .thenAnswer(invocation ->
            Optional.ofNullable(
                storedRequests.get(invocation.getArgument(0))));

    doAnswer(invocation -> {
      PendingTicketChange request = invocation.getArgument(0);
      storedRequests.put(request.requestId(), request);
      return null;
    }).when(repository).save(any(PendingTicketChange.class));

    when(repository.claimForApproval(any(UUID.class)))
        .thenAnswer(invocation -> {
          UUID requestId = invocation.getArgument(0);
          PendingTicketChange request = storedRequests.get(requestId);

          if (request == null
              || !"PENDING_APPROVAL".equals(request.state())) {
            return false;
          }

          storedRequests.put(requestId, new PendingTicketChange(
              request.requestId(),
              request.ticketId(),
              request.requestedStatus(),
              request.requestedBy(),
              request.createdAt(),
              "APPROVING"));

          return true;
        });

    service = new TicketStatusChangeService(ticketService, repository);
  }

  @Test
  void shouldCreatePendingRequestForValidChange() {
    PendingTicketChange request =
        service.requestChange("IT-1042", "Closed", "alice");

    assertEquals("IT-1042", request.ticketId());
    assertEquals("CLOSED", request.requestedStatus());
    assertEquals("PENDING_APPROVAL", request.state());

    verify(repository).save(request);
  }

  @Test
  void shouldStoreRequestSoItCanBeRetrieved() {
    PendingTicketChange created = service.requestChange(
        "IT-1042",
        "Resolved",
        "alice"
    );

    PendingTicketChange found =
        service.findPendingChange(created.requestId());

    assertEquals(created, found);
  }

  @Test
  void shouldReturnNullForUnknownRequestId() {
    assertNull(service.findPendingChange(UUID.randomUUID()));
  }

  @Test
  void shouldRejectInvalidTicketId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.requestChange("BAD-123", "Closed", "alice")
    );
  }

  @Test
  void shouldRejectNullTicketId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.requestChange(null, "Closed", "alice")
    );
  }

  @Test
  void shouldRejectUnsupportedStatus() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.requestChange("IT-1042", "Delete", "alice")
    );
  }

  @Test
  void shouldRejectNullStatus() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.requestChange("IT-1042", null, "alice")
    );
  }

  @Test
  void shouldRejectBlankRequester() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.requestChange("IT-1042", "Closed", " ")
    );
  }

  @Test
  void shouldNormalizeTicketIdAndStatus() {
    PendingTicketChange request = service.requestChange(
        " it-1042 ",
        " closed ",
        "alice"
    );

    assertEquals("IT-1042", request.ticketId());
    assertEquals("CLOSED", request.requestedStatus());
  }

  private UsernamePasswordAuthenticationToken authenticateAs(String role) {
    return new UsernamePasswordAuthenticationToken(
        "alice",
        "password",
        java.util.List.of(new SimpleGrantedAuthority(role))
    );
  }


  @Test
  void shouldApprovePendingChangeForAdmin() {
    PendingTicketChange request =
        service.requestChange("IT-1042", "Closed", "alice");

    when(ticketService.updateTicketStatus("IT-1042", "CLOSED"))
        .thenReturn(true);

    String result = service.approveChange(
        request.requestId(),
        authenticateAs("ROLE_IT_ADMIN"));

    assertTrue(result.contains("APPROVED"));
    assertTrue(result.contains("IT-1042"));

    PendingTicketChange updated =
        service.findPendingChange(request.requestId());

    assertEquals("APPROVING", updated.state());

    verify(ticketService).updateTicketStatus("IT-1042", "CLOSED");
  }

  @Test
  void shouldDenyApprovalForRegularEmployee() {
    PendingTicketChange request =
        service.requestChange("IT-1042", "Closed", "alice");

    String result = service.approveChange(
        request.requestId(),
        authenticateAs("ROLE_EMPLOYEE")
    );

    assertTrue(result.contains("ACCESS_DENIED"));
    assertEquals(
        "PENDING_APPROVAL",
        service.findPendingChange(request.requestId()).state()
    );

    verify(ticketService, never())
        .updateTicketStatus(anyString(), anyString());
  }

  @Test
  void shouldRejectUnknownApprovalRequest() {
    String result = service.approveChange(
        UUID.randomUUID(),
        authenticateAs("ROLE_IT_ADMIN")
    );

    assertTrue(result.contains("NOT_FOUND"));

    verify(ticketService, never())
        .updateTicketStatus(anyString(), anyString());
  }

  @Test
  void shouldRejectSecondApprovalOfSameRequest() {
    PendingTicketChange request =
        service.requestChange("IT-1042", "Closed", "alice");

    when(ticketService.updateTicketStatus("IT-1042", "CLOSED"))
        .thenReturn(true);

    var admin = authenticateAs("ROLE_IT_ADMIN");

    String firstResult =
        service.approveChange(request.requestId(), admin);

    String secondResult =
        service.approveChange(request.requestId(), admin);

    assertTrue(firstResult.contains("APPROVED"));
    assertTrue(secondResult.contains("INVALID_STATE"));

    verify(ticketService, times(1))
        .updateTicketStatus("IT-1042", "CLOSED");
  }

  @Test
  void shouldKeepRequestPendingWhenDatabaseUpdateFails() {
    PendingTicketChange request =
        service.requestChange("IT-1042", "Closed", "alice");

    when(ticketService.updateTicketStatus("IT-1042", "CLOSED"))
        .thenReturn(false);

    assertThrows(
        IllegalStateException.class,
        () -> service.approveChange(
            request.requestId(),
            authenticateAs("ROLE_IT_ADMIN")));

    verify(repository).claimForApproval(request.requestId());
  }


}
