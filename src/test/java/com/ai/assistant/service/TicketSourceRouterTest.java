
package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketSourceRouterTest {

  private final TicketSourceRouter router =
      new TicketSourceRouter();

  @Test
  void shouldRouteInternalTicketToPostgres() {
    var route = router.route(
        "What is the status of IT-1042?");

    assertThat(route).isPresent();
    assertThat(route.get().ticketId()).isEqualTo("IT-1042");
    assertThat(route.get().source())
        .isEqualTo(TicketSourceRouter.TicketSource.POSTGRES);
  }

  @Test
  void shouldRouteYouTrackTicketToYouTrack() {
    var route = router.route(
        "What is the status of DEMO-1?");

    assertThat(route).isPresent();
    assertThat(route.get().ticketId()).isEqualTo("DEMO-1");
    assertThat(route.get().source())
        .isEqualTo(TicketSourceRouter.TicketSource.YOUTRACK);
  }

  @Test
  void shouldRejectUnknownTicketPrefix() {
    var route = router.route(
        "What is the status of ABC-123?");

    assertThat(route).isPresent();
    assertThat(route.get().source())
        .isEqualTo(TicketSourceRouter.TicketSource.UNKNOWN);
  }

  @Test
  void shouldReturnEmptyWhenThereIsNoTicketId() {
    assertThat(router.route(
        "How do I request VPN access?"))
        .isEmpty();
  }
}
