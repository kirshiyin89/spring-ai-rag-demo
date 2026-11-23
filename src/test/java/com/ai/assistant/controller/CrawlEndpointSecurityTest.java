package com.ai.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import com.ai.assistant.config.SecurityAccountsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CrawlEndpointSecurityTest {

  private static final String CRAWL_URL = "/api/chat/crawl";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SecurityAccountsProperties accounts;

  @Test
  void shouldRejectCrawlWithoutCredentials() throws Exception {
    mockMvc.perform(post(CRAWL_URL)
            .with(SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldForbidBrewlyCustomerFromCrawling() throws Exception {
    var brewly = accounts.withRole("USER").orElseThrow();
    mockMvc.perform(post(CRAWL_URL)
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                brewly.username(),
                brewly.password())))
        .andExpect(status().isForbidden());
  }
}
