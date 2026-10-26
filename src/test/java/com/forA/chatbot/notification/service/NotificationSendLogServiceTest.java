package com.forA.chatbot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.notification.domain.NotificationSendLog;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.repository.NotificationSendLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class NotificationSendLogServiceTest {

  @InjectMocks
  private NotificationSendLogService sendLogService;

  @Mock
  private NotificationSendLogRepository sendLogRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  private static final NotificationType NOTIFICATION_TYPE = NotificationType.MEDICATION;
  private static final Long TARGET_ID = 42L;
  private static final Long DEVICE_TOKEN_ID = 10L;
  private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 4, 4);

  @BeforeEach
  void setUpTransactionManager() {
    given(transactionManager.getTransaction(any()))
        .willAnswer(invocation -> new SimpleTransactionStatus());
  }

  @Nested
  @DisplayName("claim()")
  class ClaimTest {

    @Test
    @DisplayName("returns CLAIMED when a new row is inserted")
    void claim_noExistingRow_insertsAndReturnsTrue() {
      LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(2);
      given(sendLogRepository.saveAndFlush(any(NotificationSendLog.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      NotificationSendLogService.ClaimResult result = sendLogService.claim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, lockUntil);

      assertThat(result).isEqualTo(NotificationSendLogService.ClaimResult.CLAIMED);
      verify(sendLogRepository).saveAndFlush(any(NotificationSendLog.class));
    }

    @Test
    @DisplayName("returns CLAIMED when duplicate insert falls back to claimExisting")
    void claim_ukConflict_atomicUpdateSucceeds_returnsTrue() {
      LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(2);
      given(sendLogRepository.saveAndFlush(any(NotificationSendLog.class)))
          .willThrow(new DataIntegrityViolationException("UK violation"));
      given(sendLogRepository.claimExisting(any(), any(), any(), any(), any(), any()))
          .willReturn(1);

      NotificationSendLogService.ClaimResult result = sendLogService.claim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, lockUntil);

      assertThat(result).isEqualTo(NotificationSendLogService.ClaimResult.CLAIMED);
      verify(sendLogRepository).claimExisting(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns ALREADY_SENT when duplicate insert finds an already sent row")
    void claim_ukConflict_whenAlreadySent_returnsAlreadySent() {
      LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(2);
      given(sendLogRepository.saveAndFlush(any(NotificationSendLog.class)))
          .willThrow(new DataIntegrityViolationException("UK violation"));
      given(sendLogRepository.claimExisting(any(), any(), any(), any(), any(), any()))
          .willReturn(0);
      given(sendLogRepository.findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
          NOTIFICATION_TYPE, TARGET_ID, SCHEDULED_DATE, DEVICE_TOKEN_ID))
          .willReturn(Optional.of(NotificationSendLog.builder()
              .notificationType(NOTIFICATION_TYPE)
              .targetId(TARGET_ID)
              .deviceTokenId(DEVICE_TOKEN_ID)
              .scheduledDate(SCHEDULED_DATE)
              .status("SENT")
              .build()));

      NotificationSendLogService.ClaimResult result = sendLogService.claim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, lockUntil);

      assertThat(result).isEqualTo(NotificationSendLogService.ClaimResult.ALREADY_SENT);
      verify(sendLogRepository).findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
          NOTIFICATION_TYPE, TARGET_ID, SCHEDULED_DATE, DEVICE_TOKEN_ID);
    }

    @Test
    @DisplayName("returns LOCKED_BY_ACTIVE_PROCESSOR when duplicate insert finds an active lock")
    void claim_ukConflict_whenActiveLockExists_returnsLocked() {
      LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(2);
      given(sendLogRepository.saveAndFlush(any(NotificationSendLog.class)))
          .willThrow(new DataIntegrityViolationException("UK violation"));
      given(sendLogRepository.claimExisting(any(), any(), any(), any(), any(), any()))
          .willReturn(0);
      given(sendLogRepository.findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
          NOTIFICATION_TYPE, TARGET_ID, SCHEDULED_DATE, DEVICE_TOKEN_ID))
          .willReturn(Optional.of(NotificationSendLog.builder()
              .notificationType(NOTIFICATION_TYPE)
              .targetId(TARGET_ID)
              .deviceTokenId(DEVICE_TOKEN_ID)
              .scheduledDate(SCHEDULED_DATE)
              .status("PROCESSING")
              .lockUntil(lockUntil)
              .build()));

      NotificationSendLogService.ClaimResult result = sendLogService.claim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, lockUntil);

      assertThat(result)
          .isEqualTo(NotificationSendLogService.ClaimResult.LOCKED_BY_ACTIVE_PROCESSOR);
      verify(sendLogRepository).findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
          NOTIFICATION_TYPE, TARGET_ID, SCHEDULED_DATE, DEVICE_TOKEN_ID);
    }

    @Test
    @DisplayName("returns RETRY_REQUIRED when duplicate insert finds a non terminal unsent row")
    void claim_ukConflict_nonTerminalUnsentRow_returnsRetryRequired() {
      LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(2);
      given(sendLogRepository.saveAndFlush(any(NotificationSendLog.class)))
          .willThrow(new DataIntegrityViolationException("UK violation"));
      given(sendLogRepository.claimExisting(any(), any(), any(), any(), any(), any()))
          .willReturn(0);
      given(sendLogRepository.findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
          NOTIFICATION_TYPE, TARGET_ID, SCHEDULED_DATE, DEVICE_TOKEN_ID))
          .willReturn(Optional.of(NotificationSendLog.builder()
              .notificationType(NOTIFICATION_TYPE)
              .targetId(TARGET_ID)
              .deviceTokenId(DEVICE_TOKEN_ID)
              .scheduledDate(SCHEDULED_DATE)
              .status("PENDING")
              .build()));

      NotificationSendLogService.ClaimResult result = sendLogService.claim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, lockUntil);

      assertThat(result).isEqualTo(NotificationSendLogService.ClaimResult.RETRY_REQUIRED);
      verify(sendLogRepository).claimExisting(any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("markSent()")
  class MarkSentTest {

    @Test
    @DisplayName("updates the row status to SENT")
    void markSent_updatesStatus() {
      LocalDateTime sentAt = LocalDateTime.of(2026, 4, 4, 12, 0);
      given(sendLogRepository.markSent(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, sentAt))
          .willReturn(1);

      sendLogService.markSent(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, sentAt);

      verify(sendLogRepository).markSent(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, sentAt);
    }
  }

  @Nested
  @DisplayName("releaseClaim()")
  class ReleaseClaimTest {

    @Test
    @DisplayName("updates the row status to PENDING and stores the error")
    void releaseClaim_updatesStatusAndError() {
      String errorMsg = "FCM send returned false";
      given(sendLogRepository.releaseClaim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, errorMsg))
          .willReturn(1);

      sendLogService.releaseClaim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, errorMsg);

      verify(sendLogRepository).releaseClaim(
          NOTIFICATION_TYPE, TARGET_ID, DEVICE_TOKEN_ID, SCHEDULED_DATE, errorMsg);
    }
  }
}
