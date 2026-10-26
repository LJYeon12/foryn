package com.forA.chatbot.notification.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.auth.filter.OidcTokenFilter;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.config.InternalSecurityConfig;
import com.forA.chatbot.notification.service.DiaryReminderNotificationService;
import com.forA.chatbot.notification.service.MedicationSchedulerService;
import com.forA.chatbot.notification.service.MedicationOutboxDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalNotificationController.class)
@AutoConfigureMockMvc
@Import({InternalSecurityConfig.class, OidcTokenFilter.class})
@TestPropertySource(properties = {
    "cloud-tasks.target-base-url=https://test.example.com",
    "cloud-tasks.task-execution-sa-email=test@test.iam.gserviceaccount.com"
})
class InternalNotificationSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MedicationSchedulerService medicationSchedulerService;

  @MockitoBean
  private MedicationOutboxDispatcher medicationOutboxDispatcher;

  @MockitoBean
  private DiaryReminderNotificationService diaryReminderNotificationService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  @DisplayName("Outbox 복구와 수동 재시도 경로는 OIDC 없이 실행할 수 없다")
  void medicationOutbox_withoutOidcToken_isRejected() throws Exception {
    mockMvc.perform(post("/internal/notifications/medication-outbox/recover"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/internal/notifications/medication-outbox/1/retry"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(medicationOutboxDispatcher);
  }

  @Test
  @DisplayName("internal endpoint without OIDC token returns 401")
  void scheduleDailyBatch_withoutOidcToken_returnsUnauthorized() throws Exception {
    mockMvc.perform(post("/internal/notifications/schedule-daily"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(medicationSchedulerService);
  }
}
