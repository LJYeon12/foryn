package com.forA.chatbot.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.config.JpaAuditingConfig;
import com.forA.chatbot.notification.domain.NotificationSendLog;
import com.forA.chatbot.notification.domain.NotificationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class NotificationSendLogRepositoryTest {

  @Autowired
  private NotificationSendLogRepository notificationSendLogRepository;

  @Test
  @DisplayName("동일 notificationType-target-date-token row는 unique constraint를 위반한다")
  void saveAndFlush_duplicateCompositeKey_throwsDataIntegrityViolationException() {
    LocalDate scheduledDate = LocalDate.of(2026, 4, 6);
    LocalDateTime lockUntil = LocalDateTime.of(2026, 4, 6, 9, 2);

    notificationSendLogRepository.saveAndFlush(NotificationSendLog.builder()
        .notificationType(NotificationType.DIARY_REMINDER)
        .targetId(1L)
        .deviceTokenId(2L)
        .scheduledDate(scheduledDate)
        .status("PROCESSING")
        .lockUntil(lockUntil)
        .build());

    assertThatThrownBy(() -> notificationSendLogRepository.saveAndFlush(NotificationSendLog.builder()
            .notificationType(NotificationType.DIARY_REMINDER)
            .targetId(1L)
            .deviceTokenId(2L)
            .scheduledDate(scheduledDate)
            .status("PROCESSING")
            .lockUntil(lockUntil)
            .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("발송 로그는 생성/수정 시간을 BaseTimeEntity로 기록한다")
  void saveAndFlush_setsBaseTimeColumns() {
    NotificationSendLog sendLog = notificationSendLogRepository.saveAndFlush(
        NotificationSendLog.builder()
            .notificationType(NotificationType.MEDICATION)
            .targetId(1L)
            .deviceTokenId(3L)
            .scheduledDate(LocalDate.of(2026, 4, 7))
            .status("PROCESSING")
            .build());

    assertThat(sendLog.getCreatedAt()).isNotNull();
    assertThat(sendLog.getUpdatedAt()).isNotNull();
  }
}
