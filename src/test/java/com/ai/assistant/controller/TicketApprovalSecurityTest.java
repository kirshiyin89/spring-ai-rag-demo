package com.ai.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TicketApprovalSecurityTest {

    private static final String APPROVE_URL =
            "/api/tickets/changes/11111111-1111-4111-8111-111111111111/approve";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.security.it-support.username}")
    private String itSupportUsername;

    @Value("${app.security.it-support.password}")
    private String itSupportPassword;

    // CSRF is enabled in SecurityConfig. Without a CSRF token, a POST is
    // rejected with 403 before authentication is checked. csrf() adds a
    // valid token, so these tests check authentication and roles only.

    @Test
    void shouldRejectRequestWithoutCredentials() throws Exception {
        mockMvc.perform(post(APPROVE_URL)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidUserWithoutItAdminRole() throws Exception {
        mockMvc.perform(post(APPROVE_URL)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                        itSupportUsername,
                        itSupportPassword)))
            .andExpect(status().isForbidden());
    }
}
