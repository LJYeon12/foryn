package com.forA.chatbot.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.event.MedicationBundleChangedEvent;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.service.MedicationScheduleOutboxWriter;
import com.forA.chatbot.notification.client.CloudTasksClient;
import com.forA.chatbot.notification.converter.NotificationConverter;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MedicationSchedulerServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private MedicationBundleRepository medicationBundleRepository;

  @Mock
  private DeviceTokenRepository deviceTokenRepository;

  @Mock
  private NotificationService notificationService;

  @Mock
  private NotificationSendLogService sendLogService;

  @Mock
  private CloudTasksClient cloudTasksClient;

  @Spy
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Mock
  private NotificationConverter notificationConverter;

  @Mock
  private MedicationScheduleOutboxWriter outboxWriter;

  @Mock
  private MedicationOutboxDispatcher outboxDispatcher;

  @InjectMocks
  private MedicationSchedulerService medicationSchedulerService;

  @Test
  @DisplayName("일별 배치는 외부 호출 대신 예약 의도를 영속화한다")
  void scheduleDailyBatch_recordsIntent() {
    LocalDate date = LocalDate.now(KST).plusDays(1);
    MedicationBundle bundle = medicationBundle(42L, LocalTime.of(0, 0));
    given(medicationBundleRepository.findByDayOfWeekContainingAndIsDeletedFalse(date.getDayOfWeek().name()))
        .willReturn(List.of(bundle));

    medicationSchedulerService.scheduleDailyBatch(date);

    verify(outboxWriter).recordDaily(42L, date);
    verify(cloudTasksClient, never()).createTask(any(), any(), any(), any());
  }

  @Test
  @DisplayName("한 계획의 Outbox 저장이 실패해도 나머지 계획을 처리한 뒤 실패를 반환한다")
  void scheduleDailyBatch_continuesAfterWriteFailure() {
    LocalDate date = LocalDate.now(KST).plusDays(1);
    given(medicationBundleRepository.findByDayOfWeekContainingAndIsDeletedFalse(date.getDayOfWeek().name()))
        .willReturn(List.of(medicationBundle(42L, LocalTime.MIDNIGHT),
            medicationBundle(43L, LocalTime.MIDNIGHT)));
    org.mockito.BDDMockito.willThrow(new IllegalStateException("db unavailable"))
        .given(outboxWriter).recordDaily(42L, date);

    assertThatThrownBy(() -> medicationSchedulerService.scheduleDailyBatch(date))
        .isInstanceOf(IllegalStateException.class);

    verify(outboxWriter).recordDaily(43L, date);
  }

  @Test
  @DisplayName("handleSendTask marks sent when FCM delivery succeeds")
  void handleSendTask_marksSentOnSuccess() {
    LocalDate scheduledDate = LocalDate.now(KST);
    MedicationBundle bundle = medicationBundle(42L, LocalTime.of(0, 0));
    DeviceToken deviceToken = deviceToken(10L, bundle.getUser());
    FcmNotificationRequestDto request = medicationRequest(deviceToken);

    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));
    given(deviceTokenRepository.findByUserAndIsPushEnabledTrue(bundle.getUser()))
        .willReturn(List.of(deviceToken));
    given(sendLogService.claim(eq(42L), eq(10L), eq(scheduledDate), any()))
        .willReturn(NotificationSendLogService.ClaimResult.CLAIMED);
    given(notificationConverter.toMedicationRequest(deviceToken, bundle)).willReturn(request);
    given(notificationService.sendNotification(request)).willReturn(true);

    medicationSchedulerService.handleSendTask(42L, scheduledDate);

    verify(sendLogService).markSent(eq(42L), eq(10L), eq(scheduledDate), any());
    verify(sendLogService, never()).releaseClaim(any(), any(), any(), any());
  }

  @Test
  @DisplayName("handleSendTask releases claim and throws when FCM delivery fails")
  void handleSendTask_releasesClaimOnFailure() {
    LocalDate scheduledDate = LocalDate.now(KST);
    MedicationBundle bundle = medicationBundle(42L, LocalTime.of(0, 0));
    DeviceToken deviceToken = deviceToken(10L, bundle.getUser());
    FcmNotificationRequestDto request = medicationRequest(deviceToken);

    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));
    given(deviceTokenRepository.findByUserAndIsPushEnabledTrue(bundle.getUser()))
        .willReturn(List.of(deviceToken));
    given(sendLogService.claim(eq(42L), eq(10L), eq(scheduledDate), any()))
        .willReturn(NotificationSendLogService.ClaimResult.CLAIMED);
    given(notificationConverter.toMedicationRequest(deviceToken, bundle)).willReturn(request);
    given(notificationService.sendNotification(request)).willReturn(false);

    assertThatThrownBy(() -> medicationSchedulerService.handleSendTask(42L, scheduledDate))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bundleId=42");

    verify(sendLogService).releaseClaim(42L, 10L, scheduledDate, "FCM send returned false");
  }

  @Test
  @DisplayName("handleSendTask keeps successful token state when another token fails")
  void handleSendTask_marksSuccessBeforeThrowingForFailure() {
    LocalDate scheduledDate = LocalDate.now(KST);
    MedicationBundle bundle = medicationBundle(42L, LocalTime.of(0, 0));
    DeviceToken firstDeviceToken = deviceToken(10L, bundle.getUser());
    DeviceToken secondDeviceToken = deviceToken(11L, bundle.getUser());
    FcmNotificationRequestDto firstRequest = medicationRequest(firstDeviceToken);
    FcmNotificationRequestDto secondRequest = medicationRequest(secondDeviceToken);

    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));
    given(deviceTokenRepository.findByUserAndIsPushEnabledTrue(bundle.getUser()))
        .willReturn(List.of(firstDeviceToken, secondDeviceToken));
    given(sendLogService.claim(eq(42L), eq(10L), eq(scheduledDate), any()))
        .willReturn(NotificationSendLogService.ClaimResult.CLAIMED);
    given(sendLogService.claim(eq(42L), eq(11L), eq(scheduledDate), any()))
        .willReturn(NotificationSendLogService.ClaimResult.CLAIMED);
    given(notificationConverter.toMedicationRequest(firstDeviceToken, bundle))
        .willReturn(firstRequest);
    given(notificationConverter.toMedicationRequest(secondDeviceToken, bundle))
        .willReturn(secondRequest);
    given(notificationService.sendNotification(firstRequest)).willReturn(true);
    given(notificationService.sendNotification(secondRequest)).willReturn(false);

    assertThatThrownBy(() -> medicationSchedulerService.handleSendTask(42L, scheduledDate))
        .isInstanceOf(IllegalStateException.class);

    verify(sendLogService).markSent(eq(42L), eq(10L), eq(scheduledDate), any());
    verify(sendLogService).releaseClaim(42L, 11L, scheduledDate, "FCM send returned false");
  }

  @Test
  @DisplayName("handleSendTask throws and skips FCM send when claim is blocked by an active lock")
  void handleSendTask_throwsWhenClaimBlockedByActiveLock() {
    LocalDate scheduledDate = LocalDate.now(KST);
    MedicationBundle bundle = medicationBundle(42L, LocalTime.of(0, 0));
    DeviceToken deviceToken = deviceToken(10L, bundle.getUser());

    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));
    given(deviceTokenRepository.findByUserAndIsPushEnabledTrue(bundle.getUser()))
        .willReturn(List.of(deviceToken));
    given(sendLogService.claim(eq(42L), eq(10L), eq(scheduledDate), any()))
        .willReturn(NotificationSendLogService.ClaimResult.LOCKED_BY_ACTIVE_PROCESSOR);

    assertThatThrownBy(() -> medicationSchedulerService.handleSendTask(42L, scheduledDate))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bundleId=42");

    verify(notificationConverter, never()).toMedicationRequest(any(), any());
    verify(notificationService, never()).sendNotification(any());
    verify(sendLogService, never()).markSent(any(), any(), any(), any());
    verify(sendLogService, never()).releaseClaim(any(), any(), any(), any());
  }

  @Test
  @DisplayName("이전 예약 버전은 발송 로그 선점과 FCM 호출 전에 무시한다")
  void handleSendTask_ignoresOldVersion() {
    MedicationBundle bundle = medicationBundle(42L, LocalTime.MIDNIGHT);
    ReflectionTestUtils.setField(bundle, "scheduleVersion", 2L);
    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));

    medicationSchedulerService.handleSendTask(42L, LocalDate.now(KST), 1L);

    verifyNoInteractions(deviceTokenRepository, sendLogService, notificationService);
  }

  @Test
  @DisplayName("버전 없는 기존 예약도 계획이 변경된 뒤에는 발송하지 않는다")
  void handleSendTask_ignoresLegacyTaskAfterUpdate() {
    MedicationBundle bundle = medicationBundle(42L, LocalTime.MIDNIGHT);
    ReflectionTestUtils.setField(bundle, "scheduleVersion", 1L);
    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));

    medicationSchedulerService.handleSendTask(42L, LocalDate.now(KST));

    verifyNoInteractions(deviceTokenRepository, sendLogService, notificationService);
  }

  @Test
  @DisplayName("날짜가 지난 예약은 늦게 도착해도 발송하지 않는다")
  void handleSendTask_ignoresExpiredDate() {
    given(medicationBundleRepository.findById(42L))
        .willReturn(Optional.of(medicationBundle(42L, LocalTime.MIDNIGHT)));

    medicationSchedulerService.handleSendTask(42L, LocalDate.now(KST).minusDays(7), 0L);

    verifyNoInteractions(deviceTokenRepository, sendLogService, notificationService);
  }

  @Test
  @DisplayName("예약 버전이 바뀌어도 기존 날짜별 발송 완료 기록은 재사용한다")
  void handleSendTask_preservesLogicalDeliveryDeduplication() {
    MedicationBundle bundle = medicationBundle(42L, LocalTime.MIDNIGHT);
    ReflectionTestUtils.setField(bundle, "scheduleVersion", 3L);
    DeviceToken device = deviceToken(10L, bundle.getUser());
    LocalDate date = LocalDate.now(KST);
    given(medicationBundleRepository.findById(42L)).willReturn(Optional.of(bundle));
    given(deviceTokenRepository.findByUserAndIsPushEnabledTrue(bundle.getUser())).willReturn(List.of(device));
    given(sendLogService.claim(eq(42L), eq(10L), eq(date), any()))
        .willReturn(NotificationSendLogService.ClaimResult.ALREADY_SENT);

    medicationSchedulerService.handleSendTask(42L, date, 3L);

    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("계획 변경은 기존 Task ID를 삭제 후 재사용하지 않는다")
  void handleBundleChangedEvent_doesNotDeleteExistingTask() {
    medicationSchedulerService.handleBundleChangedEvent(
        new MedicationBundleChangedEvent(42L, MedicationBundleChangedEvent.ChangeType.UPDATED));

    verify(cloudTasksClient, never()).deleteTask(any());
  }

  @Test
  @DisplayName("설정 변경 이벤트는 저장된 Outbox의 처리를 깨운다")
  void handleBundleChangedEvent_wakesDispatcher() {
    medicationSchedulerService.handleBundleChangedEvent(
        new MedicationBundleChangedEvent(42L, MedicationBundleChangedEvent.ChangeType.DELETED));

    verify(outboxDispatcher).dispatchBundle(42L);
    verify(cloudTasksClient, never()).deleteTask(any());
  }

  private MedicationBundle medicationBundle(Long bundleId, LocalTime scheduledUtcTime) {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = MedicationBundle.builder()
        .user(user)
        .bundleName("vitamin")
        .dayOfWeek(LocalDate.now(KST).getDayOfWeek().name())
        .scheduledTime(scheduledUtcTime)
        .build();
    ReflectionTestUtils.setField(bundle, "id", bundleId);
    return bundle;
  }

  private DeviceToken deviceToken(Long deviceTokenId, User user) {
    DeviceToken deviceToken = DeviceToken.builder()
        .user(user)
        .deviceToken("device-token-" + deviceTokenId)
        .isPushEnabled(true)
        .build();
    ReflectionTestUtils.setField(deviceToken, "id", deviceTokenId);
    return deviceToken;
  }

  private FcmNotificationRequestDto medicationRequest(DeviceToken deviceToken) {
    return FcmNotificationRequestDto.builder()
        .deviceToken(deviceToken.getDeviceToken())
        .notificationType(NotificationType.MEDICATION)
        .title("title")
        .body("body")
        .build();
  }
}
