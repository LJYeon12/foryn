package com.forA.chatbot.notification.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.auth.filter.OidcTokenFilter;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.notification.service.DiaryReminderNotificationService;
import com.forA.chatbot.notification.service.MedicationSchedulerService;
import com.forA.chatbot.notification.service.MedicationOutboxDispatcher;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalNotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalNotificationControllerTest {

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

  @MockitoBean
  private OidcTokenFilter oidcTokenFilter;

  @Test
  @DisplayName("schedule-daily without date delegates null to service")
  void scheduleDailyBatch_withoutDate_callsServiceWithNull() throws Exception {
    mockMvc.perform(post("/internal/notifications/schedule-daily"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result").value("Daily batch 완료"));

    verify(medicationSchedulerService).scheduleDailyBatch(isNull());
    verify(diaryReminderNotificationService).scheduleDailyBatch(isNull());
  }

  @Test
  @DisplayName("schedule-daily with date delegates parsed LocalDate to service")
  void scheduleDailyBatch_withDate_callsServiceWithDate() throws Exception {
    mockMvc.perform(post("/internal/notifications/schedule-daily")
            .param("scheduledDate", "2026-04-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true));

    verify(medicationSchedulerService).scheduleDailyBatch(LocalDate.of(2026, 4, 7));
    verify(diaryReminderNotificationService).scheduleDailyBatch(LocalDate.of(2026, 4, 7));
  }

  @Test
  @DisplayName("schedule-daily returns 500 when service throws")
  void scheduleDailyBatch_whenServiceThrows_returnsInternalServerError() throws Exception {
    willThrow(new IllegalStateException("boom"))
        .given(medicationSchedulerService)
        .scheduleDailyBatch(isNull());

    mockMvc.perform(post("/internal/notifications/schedule-daily"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value("COMMON500"));

    verify(diaryReminderNotificationService).scheduleDailyBatch(isNull());
  }

  @Test
  @DisplayName("send endpoint delegates bundle id and scheduled date to service")
  void sendNotification_callsService() throws Exception {
    mockMvc.perform(post("/internal/notifications/send/42")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "bundleId": 42,
                  "scheduledDate": "2026-04-07"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result").value("발송 처리 완료"));

    verify(medicationSchedulerService).handleSendTask(42L, LocalDate.of(2026, 4, 7), null);
  }

  @Test
  @DisplayName("send endpoint returns 500 when service throws")
  void sendNotification_whenServiceThrows_returnsInternalServerError() throws Exception {
    willThrow(new IllegalStateException("boom"))
        .given(medicationSchedulerService)
        .handleSendTask(42L, LocalDate.of(2026, 4, 7), null);

    mockMvc.perform(post("/internal/notifications/send/42")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "bundleId": 42,
                  "scheduledDate": "2026-04-07"
                }
                """))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value("COMMON500"));
  }

  @Test
  @DisplayName("버전 있는 발송 요청은 버전을 그대로 전달한다")
  void sendNotification_passesScheduleVersion() throws Exception {
    mockMvc.perform(post("/internal/notifications/send/42")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bundleId\":42,\"scheduledDate\":\"2026-10-19\",\"scheduleVersion\":3}"))
        .andExpect(status().isOk());

    verify(medicationSchedulerService).handleSendTask(42L, LocalDate.of(2026, 10, 19), 3L);
  }

  @Test
  @DisplayName("복구 요청은 DB에 남은 예약 작업을 다시 처리한다")
  void recoverMedicationOutbox_callsDispatcher() throws Exception {
    mockMvc.perform(post("/internal/notifications/medication-outbox/recover"))
        .andExpect(status().isOk());

    verify(medicationOutboxDispatcher).recoverAndDispatch();
  }

  @Test
  @DisplayName("실패 종료 작업은 명시적인 재시도 요청으로 처리한다")
  void retryMedicationOutbox_callsDispatcher() throws Exception {
    mockMvc.perform(post("/internal/notifications/medication-outbox/52/retry"))
        .andExpect(status().isOk());

    verify(medicationOutboxDispatcher).retryFailed(52L);
  }

  @Test
  @DisplayName("한줄일기 reminder send endpoint는 reminder id와 scheduled date를 service에 전달한다")
  void sendDiaryReminderNotification_callsService() throws Exception {
    mockMvc.perform(post("/internal/notifications/diary-reminders/send/10")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "reminderId": 10,
                  "scheduledDate": "2026-06-22"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result").value("한줄일기 알림 발송 처리 완료"));

    verify(diaryReminderNotificationService).handleSendTask(10L, LocalDate.of(2026, 6, 22));
  }

  @Test
  @DisplayName("한줄일기 재예약 job endpoint는 job id를 service에 전달한다")
  void processDiaryReminderRescheduleJob_callsService() throws Exception {
    mockMvc.perform(post("/internal/notifications/diary-reminders/reschedule-jobs/50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result").value("한줄일기 알림 재예약 처리 완료"));

    verify(diaryReminderNotificationService).processRescheduleJob(50L);
  }
}
