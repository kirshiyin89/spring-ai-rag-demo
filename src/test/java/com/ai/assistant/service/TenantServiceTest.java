package com.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.assistant.config.TenancyProperties;
import com.ai.assistant.persistence.Tenant;
import com.ai.assistant.persistence.TenantRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TenantServiceTest {

  private final TenantRepository repository = mock(TenantRepository.class);

  @Test
  void resolvesEachUsernameToItsConfiguredTenant() {
    Tenant acme = tenant(1L, "acme");
    Tenant globex = tenant(2L, "globex");
    when(repository.findBySlugAndActiveTrue("acme")).thenReturn(Optional.of(acme));
    when(repository.findBySlugAndActiveTrue("globex")).thenReturn(Optional.of(globex));

    TenantService service = service(Map.of(
        "acme", List.of("alice", "bob"),
        "globex", List.of("carol")));

    assertThat(service.resolveForUsername("alice")).isSameAs(acme);
    assertThat(service.resolveForUsername("carol")).isSameAs(globex);
  }

  @Test
  void ignoresBlankUsernamesInConfiguration() {
    Tenant acme = tenant(1L, "acme");
    when(repository.findBySlugAndActiveTrue("acme")).thenReturn(Optional.of(acme));

    TenantService service = service(Map.of("acme", List.of("alice", " ", "")));

    assertThat(service.resolveForUsername("alice")).isSameAs(acme);
    assertThatThrownBy(() -> service.resolveForUsername(""))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rejectsUsersWithoutATenant() {
    TenantService service = service(Map.of("acme", List.of("alice")));

    assertThatThrownBy(() -> service.resolveForUsername("mallory"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("No tenant assigned");
  }

  @Test
  void rejectsAUserWhenTheTenantIsInactive() {
    when(repository.findBySlugAndActiveTrue(anyString())).thenReturn(Optional.empty());
    TenantService service = service(Map.of("acme", List.of("alice")));

    assertThatThrownBy(() -> service.resolveForUsername("alice"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Tenant is unavailable");
  }

  private TenantService service(Map<String, List<String>> users) {
    return new TenantService(repository, new TenancyProperties(new LinkedHashMap<>(users)));
  }

  private static Tenant tenant(Long id, String slug) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setSlug(slug);
    tenant.setName(slug);
    return tenant;
  }
}
