package com.ai.assistant.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps each tenant slug to the login usernames that belong to it.
 *
 * <pre>
 * app:
 *   tenancy:
 *     users:
 *       finflow: alice,bob
 *       brewly: carol
 * </pre>
 *
 * Adding a tenant's users is configuration only. No Java change is needed.
 */
@ConfigurationProperties(prefix = "app.tenancy")
public record TenancyProperties(Map<String, List<String>> users) {

  public TenancyProperties {
    users = users == null ? Map.of() : users;
  }
}
