package com.ai.assistant.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login accounts for the form login and HTTP Basic, bound from configuration.
 *
 * <pre>
 * app:
 *   security:
 *     accounts:
 *       - username: alice
 *         password: secret
 *         role: IT_SUPPORT
 * </pre>
 *
 * An entry with a blank username is left out, so an optional account (such as the Brewly
 * login for a hosted demo) is switched off by leaving its environment variable empty.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityAccountsProperties(List<Account> accounts) {

  public SecurityAccountsProperties {
    List<Account> configured = accounts == null ? List.of() : accounts;
    configured = configured.stream()
        .filter(Objects::nonNull)
        .filter(account -> account.username() != null && !account.username().isBlank())
        .toList();

    for (Account account : configured) {
      if (account.password() == null || account.password().isBlank()) {
        // Fail at startup: an empty password would otherwise become a working login.
        throw new IllegalStateException(
            "Account " + account.username() + " has no password configured");
      }
      if (account.role() == null || account.role().isBlank()) {
        throw new IllegalStateException(
            "Account " + account.username() + " has no role configured");
      }
    }

    long distinctUsernames = configured.stream().map(Account::username).distinct().count();
    if (distinctUsernames != configured.size()) {
      throw new IllegalStateException("Two accounts share the same username");
    }

    accounts = configured;
  }

  /** The account that has the given role, if one is configured. */
  public Optional<Account> withRole(String role) {
    return accounts.stream().filter(account -> account.role().equals(role)).findFirst();
  }

  /** One login. The role is written without the ROLE_ prefix, for example "IT_SUPPORT". */
  public record Account(String username, String password, String role) {
  }
}
