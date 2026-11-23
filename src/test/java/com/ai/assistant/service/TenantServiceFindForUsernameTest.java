package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.assistant.config.TenancyProperties;
import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.persistence.TenantRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantServiceFindForUsernameTest {

  private final TenantRepository repository = mock(TenantRepository.class);

  private final TenantService service = new TenantService(
      repository,
      new TenancyProperties(Map.of("brewly", List.of("carol"), "finflow", List.of(""))));

  @Test
  void shouldReturnTenantOfMappedUserWithLandingPath() {
    var brewly = new Tenant();
    brewly.setSlug("brewly");
    brewly.setLandingPath("/brewly.html");
    when(repository.findBySlugAndActiveTrue("brewly")).thenReturn(Optional.of(brewly));

    assertThat(service.findForUsername("carol"))
        .hasValueSatisfying(tenant -> assertThat(tenant.getLandingPath()).isEqualTo("/brewly.html"));
  }

  @Test
  void shouldReturnEmptyForUserWithoutTenant() {
    assertThat(service.findForUsername("nobody")).isEmpty();
  }

  @Test
  void shouldReturnEmptyForBlankUsername() {
    assertThat(service.findForUsername(" ")).isEmpty();
    assertThat(service.findForUsername(null)).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenTenantIsInactive() {
    when(repository.findBySlugAndActiveTrue(anyString())).thenReturn(Optional.empty());

    assertThat(service.findForUsername("carol")).isEmpty();
  }
}
