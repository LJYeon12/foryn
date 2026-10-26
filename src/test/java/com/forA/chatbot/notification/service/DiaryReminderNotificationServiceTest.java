package com.forA.chatbot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.domain.DiaryReminderRescheduleJob;
import com.forA.chatbot.diary.domain.enums.DiaryReminderRescheduleJobStatus;
import com.forA.chatbot.diary.repository.DiaryReminderRepository;
import com.forA.chatbot.diary.repository.DiaryReminderRescheduleJobRepository;
import com.forA.chatbot.notification.client.CloudTasksClient;
import com.forA.chatbot.notification.converter.NotificationConverter;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class DiaryReminderNotificationServiceTest {

  @Mock
  private DiaryReminderRepository diaryReminderRepository;

  @Mock
  private DiaryReminderRescheduleJobRepository rescheduleJobRepository;

  @Mock
  private DeviceTokenRepository deviceTokenRepository;

  @Mock
  private NotificationService notificationService;

  @Mock
  private NotificationSendLogService sendLogService;

  @Mock
  private CloudTasksClient cloudTasksClient;

  @Mock
  private NotificationConverter notificationConverter;

  @Mock
  private ObjectMapper objectMapper;

  @Spy
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Mock
  private PlatformTransactionManager transactionManager;

  @InjectMocks
  private DiaryReminderNotificationService service;

  @BeforeEach
  void setUpTransactionManager() {
    lenient().when(transactionManager.getTransaction(any()))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
  }

  @Test
  @DisplayName("daily batch는 사용자 timezone 기준으로 한줄일기 Cloud Task를 생성한다")
  void scheduleDailyBatch_createsDiaryReminderTaskWithUserTimeZone() throws Exception {
    LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    User user = user(1L, "Asia/Seoul");
    DiaryReminder reminder = reminder(10L, user, LocalTime.of(21, 0), true);
    String payload = "{\"reminderId\":10,\"scheduledDate\":\"" + scheduledDate + "\"}";

    given(diaryReminderRepository.findByEnabledTrue()).willReturn(List.of(reminder));
    given(objectMapper.writeValueAsString(any())).willReturn(payload);

    service.scheduleDailyBatch(scheduledDate);

    verify(cloudTasksClient).createTask(
        eq("diary-reminder-10-" + scheduledDate),
        eq("/internal/notifications/diary-reminders/send/10"),
        eq(payload),
        eq(ZonedDateTime.of(scheduledDate, LocalTime.of(21, 0), ZoneId.of("Asia/Seoul"))
            .toInstant()));
  }

  @Test
  @DisplayName("daily batch는 task 생성 실패 후에도 남은 reminder를 계속 처리한다")
  void scheduleDailyBatch_continuesAfterTaskCreationFailure() throws Exception {
    LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    User user = user(1L, "Asia/Seoul");
    DiaryReminder firstReminder = reminder(10L, user, LocalTime.of(9, 0), true);
    DiaryReminder failedReminder = reminder(11L, user, LocalTime.of(10, 0), true);
    DiaryReminder thirdReminder = reminder(12L, user, LocalTime.of(11, 0), true);

    given(diaryReminderRepository.findByEnabledTrue())
        .willReturn(List.of(firstReminder, failedReminder, thirdReminder));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    willThrow(new RuntimeException("gcp down"))
        .given(cloudTasksClient)
        .createTask(eq("diary-reminder-11-" + scheduledDate), any(), any(), any());

    assertThatThrownBy(() -> service.scheduleDailyBatch(scheduledDate))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("11");

    verify(cloudTasksClient).createTask(
        eq("diary-reminder-10-" + scheduledDate),
        eq("/internal/notifications/diary-reminders/send/10"),
        eq("{}"),
        eq(ZonedDateTime.of(scheduledDate, LocalTime.of(9, 0), ZoneId.of("Asia/Seoul"))
            .toInstant()));
    verify(cloudTasksClient).createTask(
        eq("diary-reminder-11-" + scheduledDate),
        eq("/internal/notifications/diary-reminders/send/11"),
        eq("{}"),
        eq(ZonedDateTime.of(scheduledDate, LocalTime.of(10, 0), ZoneId.of("Asia/Seoul"))
            .toInstant()));
    verify(cloudTasksClient).createTask(
        eq("diary-reminder-12-" + scheduledDate),
        eq("/internal/notifications/diary-reminders/send/12"),
        eq("{}"),
        eq(ZonedDateTime.of(scheduledDate, LocalTime.of(11, 0), ZoneId.of("Asia/Seoul"))
            .toInstant()));
  }

  @Test
  @DisplayName("발송 시 reminder가 삭제되었으면 no-op 성공 처리한다")
  void handleSendTask_missingReminder_noOps() {
    given(diaryReminderRepository.findById(10L)).willReturn(Optional.empty());

    service.handleSendTask(10L, LocalDate.of(2026, 6, 22));

    verify(deviceTokenRepository, never()).findByUserAndIsPushEnabledTrue(any());
    verify(notificationService, never()).sendNotification(any());
  }

  @Test
  @DisplayName("발송 시 disabled reminder면 no-op 성공 처리한다")
  void handleSendTask_disabledReminder_noOps() {
    DiaryReminder reminder =
        reminder(10L, user(1L, "Asia/Seoul"), LocalTime.of(21, 0), false);
    given(diaryReminderRepository.findById(10L)).willReturn(Optional.of(reminder));

    service.handleSendTask(10L, LocalDate.of(2026, 6, 22));

    verify(deviceTokenRepository, never()).findByUserAndIsPushEnabledTrue(any());
    verify(notificationService, never()).sendNotification(any());
  }

  @Test
  @DisplayName("한줄일기 알림 발송 성공 시 DIARY_REMINDER targetId 기준 send log를 SENT 처리한다")
  void handleSendTask_success_marksDiaryReminderSendLogSent() {
    LocalDate scheduledDate = LocalDate.of(2026, 6, 22);
    User user = user(1L, "Asia/Seoul");
    DiaryReminder reminder = reminder(10L, user, LocalTime.of(21, 0), true);
    DeviceToken deviceToken = deviceToken(100L, user);
    FcmNotificationRequestDto request = FcmNotificationRequestDto.builder()
        .deviceToken("device-token")
        .notificationType(NotificationType.DIARY_REMINDER)
        .title("한줄일기")
        .body("포린")
        .build();

    given(diaryReminderRepository.findById(10L)).willReturn(Optional.of(reminder));
    given(deviceTokenRepository.findByUserAndIsPushEnabledTrue(user)).willReturn(List.of(deviceToken));
    given(sendLogService.claim(eq(NotificationType.DIARY_REMINDER), eq(10L), eq(100L),
        eq(scheduledDate), any()))
        .willReturn(NotificationSendLogService.ClaimResult.CLAIMED);
    given(notificationConverter.toDiaryReminderRequest(deviceToken)).willReturn(request);
    given(notificationService.sendNotification(request)).willReturn(true);

    service.handleSendTask(10L, scheduledDate);

    verify(sendLogService).markSent(
        eq(NotificationType.DIARY_REMINDER), eq(10L), eq(100L), eq(scheduledDate), any());
    verify(sendLogService, never()).releaseClaim(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("재예약 job은 기존 task 삭제 후 현재 enabled reminder 기준 오늘 남은 task를 다시 만든다")
  void processRescheduleJob_deletesOldTasksAndCreatesCurrentFutureTasks() throws Exception {
    LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    User user = user(1L, "Asia/Seoul");
    DiaryReminderRescheduleJob job = job(50L, user, scheduledDate, List.of(10L, 11L), 0);
    DiaryReminder newReminder = reminder(20L, user, LocalTime.of(21, 0), true);

    given(rescheduleJobRepository.claimRunnableJob(eq(50L), anyList(), any())).willReturn(1);
    given(rescheduleJobRepository.findByIdWithUser(50L)).willReturn(Optional.of(job));
    given(rescheduleJobRepository.findById(50L)).willReturn(Optional.of(job));
    given(diaryReminderRepository.findEnabledByUserIdWithUserOrderByReminderTimeAsc(1L))
        .willReturn(List.of(newReminder));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");

    service.processRescheduleJob(50L);

    verify(cloudTasksClient).deleteTask("diary-reminder-10-" + scheduledDate);
    verify(cloudTasksClient).deleteTask("diary-reminder-11-" + scheduledDate);
    verify(cloudTasksClient).createTask(
        eq("diary-reminder-20-" + scheduledDate),
        eq("/internal/notifications/diary-reminders/send/20"),
        eq("{}"),
        eq(ZonedDateTime.of(scheduledDate, LocalTime.of(21, 0), ZoneId.of("Asia/Seoul"))
            .toInstant()));
    assertThat(job.getStatus()).isEqualTo(DiaryReminderRescheduleJobStatus.SUCCEEDED);
  }

  @Test
  @DisplayName("재예약 job은 처리 중 실패하면 Cloud Task로 다음 재시도를 예약한다")
  void processRescheduleJob_whenFailsBeforeRetryLimit_schedulesRetryTask() {
    LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    User user = user(1L, "Asia/Seoul");
    DiaryReminderRescheduleJob job = job(50L, user, scheduledDate, List.of(10L), 0);

    given(rescheduleJobRepository.claimRunnableJob(eq(50L), anyList(), any())).willReturn(1);
    given(rescheduleJobRepository.findByIdWithUser(50L)).willReturn(Optional.of(job));
    given(rescheduleJobRepository.findById(50L)).willReturn(Optional.of(job));
    willThrow(new RuntimeException("gcp down")).given(cloudTasksClient).deleteTask(any());

    service.processRescheduleJob(50L);

    assertThat(job.getStatus()).isEqualTo(DiaryReminderRescheduleJobStatus.RETRY_WAITING);
    assertThat(job.getRetryCount()).isEqualTo(1);
    verify(cloudTasksClient).createTask(
        eq("diary-reminder-reschedule-50-1"),
        eq("/internal/notifications/diary-reminders/reschedule-jobs/50"),
        eq("{}"),
        any());
  }

  @Test
  @DisplayName("재예약 job은 실패 시 3회 재시도 후 FAILED 상태가 된다")
  void processRescheduleJob_marksFailedAfterRetryLimit() {
    LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    User user = user(1L, "Asia/Seoul");
    DiaryReminderRescheduleJob job = job(50L, user, scheduledDate, List.of(10L), 3);

    given(rescheduleJobRepository.claimRunnableJob(eq(50L), anyList(), any())).willReturn(1);
    given(rescheduleJobRepository.findByIdWithUser(50L)).willReturn(Optional.of(job));
    given(rescheduleJobRepository.findById(50L)).willReturn(Optional.of(job));
    willThrow(new RuntimeException("gcp down")).given(cloudTasksClient).deleteTask(any());

    service.processRescheduleJob(50L);

    assertThat(job.getStatus()).isEqualTo(DiaryReminderRescheduleJobStatus.FAILED);
    assertThat(job.getLastError()).contains("gcp down");
  }

  @Test
  @DisplayName("재예약 job 선점에 실패하면 Cloud Tasks를 호출하지 않는다")
  void processRescheduleJob_whenClaimFails_noOps() {
    given(rescheduleJobRepository.claimRunnableJob(eq(50L), anyList(), any())).willReturn(0);

    service.processRescheduleJob(50L);

    verify(cloudTasksClient, never()).deleteTask(any());
    verify(cloudTasksClient, never()).createTask(any(), any(), any(), any());
  }

  @Test
  @DisplayName("오래된 PROCESSING 재예약 job을 다시 처리 가능하게 복구한다")
  void recoverStaleProcessingJobs_delegatesToRepository() {
    given(rescheduleJobRepository.recoverStaleProcessingJobs(any(), any())).willReturn(2);

    int recovered = service.recoverStaleProcessingJobs(Duration.ofMinutes(10));

    assertThat(recovered).isEqualTo(2);
    verify(rescheduleJobRepository).recoverStaleProcessingJobs(any(LocalDateTime.class),
        any(LocalDateTime.class));
  }

  private User user(Long userId, String timeZone) {
    User user = User.builder()
        .id(userId)
        .email("user" + userId + "@example.com")
        .fullName("User")
        .timeZone(timeZone)
        .build();
    return user;
  }

  private DiaryReminder reminder(Long reminderId, User user, LocalTime time, boolean enabled) {
    DiaryReminder reminder = DiaryReminder.builder()
        .user(user)
        .reminderTime(time)
        .enabled(enabled)
        .build();
    ReflectionTestUtils.setField(reminder, "id", reminderId);
    return reminder;
  }

  private DeviceToken deviceToken(Long tokenId, User user) {
    DeviceToken deviceToken = DeviceToken.builder()
        .user(user)
        .deviceToken("device-token")
        .isPushEnabled(true)
        .build();
    ReflectionTestUtils.setField(deviceToken, "id", tokenId);
    return deviceToken;
  }

  private DiaryReminderRescheduleJob job(
      Long jobId, User user, LocalDate date, List<Long> oldReminderIds, int retryCount) {
    DiaryReminderRescheduleJob job = DiaryReminderRescheduleJob.builder()
        .user(user)
        .scheduledDate(date)
        .oldReminderIds(oldReminderIds)
        .retryCount(retryCount)
        .status(DiaryReminderRescheduleJobStatus.PENDING)
        .build();
    ReflectionTestUtils.setField(job, "id", jobId);
    return job;
  }
}
