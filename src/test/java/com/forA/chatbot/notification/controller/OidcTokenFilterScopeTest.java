package com.forA.chatbot.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.auth.filter.OidcTokenFilter;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    value = OidcTokenFilterScopeTest.TestPublicController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
    })
@AutoConfigureMockMvc
@Import(OidcTokenFilter.class)
@TestPropertySource(properties = {
    "cloud-tasks.target-base-url=https://test.example.com",
    "cloud-tasks.task-execution-sa-email=test@test.iam.gserviceaccount.com"
})
class OidcTokenFilterScopeTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  @DisplayName("non-internal route is not blocked by OIDC filter")
  void publicRoute_withoutOidcToken_isNotBlocked() throws Exception {
    mockMvc.perform(get("/test/public"))
        .andExpect(status().isOk());
  }

  @RestController
  static class TestPublicController {

    @GetMapping("/test/public")
    String publicRoute() {
      return "ok";
    }
  }
}
