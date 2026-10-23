package com.ai.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TicketApprovalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectRequestWithoutCredentials() throws Exception {
        mockMvc.perform(post(
                "/api/tickets/changes/11111111-1111-4111-8111-111111111111/approve"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidAdminWithoutItAdminRole() throws Exception {
        mockMvc.perform(post(
                "/api/tickets/changes/11111111-1111-4111-8111-111111111111/approve")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                        "raguser",
                        "ragpassword")))
            .andExpect(status().isForbidden());
    }
}
