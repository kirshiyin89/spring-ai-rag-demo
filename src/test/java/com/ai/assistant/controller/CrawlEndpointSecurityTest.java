package com.ai.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CrawlEndpointSecurityTest {

  private static final String CRAWL_URL = "/api/chat/crawl";

  @Autowired
  private MockMvc mockMvc;

  @Value("${app.security.brewly.username}")
  private String brewlyUsername;

  @Value("${app.security.brewly.password}")
  private String brewlyPassword;

  @Test
  void shouldRejectCrawlWithoutCredentials() throws Exception {
    mockMvc.perform(post(CRAWL_URL)
            .with(SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldForbidBrewlyCustomerFromCrawling() throws Exception {
    mockMvc.perform(post(CRAWL_URL)
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                brewlyUsername,
                brewlyPassword)))
        .andExpect(status().isForbidden());
  }
}
