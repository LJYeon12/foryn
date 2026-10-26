package com.forA.chatbot.notification.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
    name = "notification_send_log",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_target_date_token",
        columnNames = {"notification_type", "target_id", "scheduled_date", "device_token_id"}
    )
)
public class NotificationSendLog extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 30)
  private NotificationType notificationType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Column(name = "device_token_id", nullable = false)
  private Long deviceTokenId;

  @Column(name = "scheduled_date", nullable = false)
  private LocalDate scheduledDate;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "lock_until")
  private LocalDateTime lockUntil;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  @Column(name = "last_error", length = 255)
  private String lastError;

  @Builder
  public NotificationSendLog(NotificationType notificationType, Long targetId,
      Long deviceTokenId, LocalDate scheduledDate, String status, LocalDateTime lockUntil) {
    this.notificationType = notificationType;
    this.targetId = targetId;
    this.deviceTokenId = deviceTokenId;
    this.scheduledDate = scheduledDate;
    this.status = status;
    this.lockUntil = lockUntil;
  }

  public boolean isSent() {
    return "SENT".equals(this.status);
  }

  public boolean isLockActive() {
    return "PROCESSING".equals(this.status)
        && this.lockUntil != null
        && this.lockUntil.isAfter(LocalDateTime.now());
  }

  public void claimForProcessing(LocalDateTime lockUntil) {
    this.status = "PROCESSING";
    this.lockUntil = lockUntil;
    this.lastError = null;
  }

  public void markSent(LocalDateTime sentAt) {
    this.status = "SENT";
    this.sentAt = sentAt;
    this.lockUntil = null;
  }

  public void releaseClaim(String errorMessage) {
    this.status = "PENDING";
    this.lockUntil = null;
    this.lastError = errorMessage;
  }
}
