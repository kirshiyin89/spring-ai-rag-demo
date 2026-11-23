package com.ai.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.assistant.config.SecurityAccountsProperties.Account;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityAccountsPropertiesTest {

  @Test
  void shouldLeaveOutAccountsWithBlankUsername() {
    // An empty environment variable switches an optional login off.
    var properties = new SecurityAccountsProperties(List.of(
        new Account("", "", "USER"),
        new Account("alice", "secret", "IT_SUPPORT")));

    assertThat(properties.accounts()).extracting(Account::username).containsExactly("alice");
    assertThat(properties.withRole("USER")).isEmpty();
  }

  @Test
  void shouldFindAccountByRole() {
    var properties = new SecurityAccountsProperties(List.of(
        new Account("alice", "secret", "IT_SUPPORT")));

    assertThat(properties.withRole("IT_SUPPORT")).map(Account::username).contains("alice");
  }

  @Test
  void shouldRejectAccountWithoutPassword() {
    // An empty password would otherwise become a working login.
    assertThatThrownBy(() -> new SecurityAccountsProperties(List.of(
        new Account("alice", "", "IT_SUPPORT"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("alice");
  }

  @Test
  void shouldRejectDuplicateUsernames() {
    assertThatThrownBy(() -> new SecurityAccountsProperties(List.of(
        new Account("alice", "one", "IT_SUPPORT"),
        new Account("alice", "two", "ADMIN"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldTreatMissingListAsNoAccounts() {
    assertThat(new SecurityAccountsProperties(null).accounts()).isEmpty();
  }
}
