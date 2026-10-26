package com.forA.chatbot.diary.domain;

import com.forA.chatbot.diary.converter.LongListJsonConverter;
import com.forA.chatbot.diary.domain.enums.DiaryReminderRescheduleJobStatus;
import com.forA.chatbot.global.BaseTimeEntity;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "diary_reminder_reschedule_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReminderRescheduleJob extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "scheduled_date", nullable = false)
  private LocalDate scheduledDate;

  @Convert(converter = LongListJsonConverter.class)
  @Column(name = "old_reminder_ids", nullable = false, columnDefinition = "JSON")
  private List<Long> oldReminderIds = new ArrayList<>();

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "next_retry_at")
  private LocalDateTime nextRetryAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private DiaryReminderRescheduleJobStatus status;

  @Column(name = "last_error", length = 255)
  private String lastError;

  @Builder
  public DiaryReminderRescheduleJob(
      User user,
      LocalDate scheduledDate,
      List<Long> oldReminderIds,
      Integer retryCount,
      LocalDateTime nextRetryAt,
      DiaryReminderRescheduleJobStatus status,
      String lastError) {
    this.user = user;
    this.scheduledDate = scheduledDate;
    this.oldReminderIds =
        oldReminderIds == null ? new ArrayList<>() : new ArrayList<>(oldReminderIds);
    this.retryCount = retryCount == null ? 0 : retryCount;
    this.nextRetryAt = nextRetryAt;
    this.status = status == null ? DiaryReminderRescheduleJobStatus.PENDING : status;
    this.lastError = lastError;
  }

  public void markProcessing() {
    this.status = DiaryReminderRescheduleJobStatus.PROCESSING;
    this.lastError = null;
  }

  public void markSucceeded() {
    this.status = DiaryReminderRescheduleJobStatus.SUCCEEDED;
    this.nextRetryAt = null;
    this.lastError = null;
  }

  public void recordFailure(String errorMessage, LocalDateTime nextRetryAt, boolean retryable) {
    this.retryCount++;
    this.lastError = errorMessage;
    if (retryable) {
      this.status = DiaryReminderRescheduleJobStatus.RETRY_WAITING;
      this.nextRetryAt = nextRetryAt;
      return;
    }
    this.status = DiaryReminderRescheduleJobStatus.FAILED;
    this.nextRetryAt = null;
  }
}
