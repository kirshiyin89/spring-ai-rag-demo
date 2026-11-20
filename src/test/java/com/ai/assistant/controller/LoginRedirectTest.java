package com.ai.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LoginRedirectTest {

  @Autowired
  private MockMvc mockMvc;

  @Value("${app.security.brewly.username}")
  private String brewlyUsername;

  @Value("${app.security.brewly.password}")
  private String brewlyPassword;

  @Value("${app.security.it-support.username}")
  private String itSupportUsername;

  @Value("${app.security.it-support.password}")
  private String itSupportPassword;

  @Test
  void shouldSendBrewlyCustomersToTheBrewlyPage() throws Exception {
    mockMvc.perform(post("/login")
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .param("username", brewlyUsername)
            .param("password", brewlyPassword))
        .andExpect(redirectedUrl("/brewly.html"));
  }

  @Test
  void shouldSendOtherUsersToTheFinFlowPage() throws Exception {
    mockMvc.perform(post("/login")
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .param("username", itSupportUsername)
            .param("password", itSupportPassword))
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void shouldRequireLoginForTheBrewlyPage() throws Exception {
    // HTTP Basic is enabled, so anonymous requests get 401 rather than a redirect to /login.
    mockMvc.perform(get("/brewly.html"))
        .andExpect(status().isUnauthorized());
  }
}
