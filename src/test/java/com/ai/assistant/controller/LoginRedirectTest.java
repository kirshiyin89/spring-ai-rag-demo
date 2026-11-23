package com.ai.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.assistant.config.SecurityAccountsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LoginRedirectTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SecurityAccountsProperties accounts;

  @Test
  void shouldSendBrewlyCustomersToTheBrewlyPage() throws Exception {
    // The landing page comes from the tenants table (changeset 011), not from code.
    var brewly = accounts.withRole("USER").orElseThrow();
    mockMvc.perform(post("/login")
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .param("username", brewly.username())
            .param("password", brewly.password()))
        .andExpect(redirectedUrl("/brewly.html"));
  }

  @Test
  void shouldSendTenantUsersWithoutCustomLandingPageToTheFinFlowPage() throws Exception {
    var itSupport = accounts.withRole("IT_SUPPORT").orElseThrow();
    mockMvc.perform(post("/login")
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .param("username", itSupport.username())
            .param("password", itSupport.password()))
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void shouldRequireLoginForTheBrewlyPage() throws Exception {
    // HTTP Basic is enabled, so anonymous requests get 401 rather than a redirect to /login.
    mockMvc.perform(get("/brewly.html"))
        .andExpect(status().isUnauthorized());
  }
}
