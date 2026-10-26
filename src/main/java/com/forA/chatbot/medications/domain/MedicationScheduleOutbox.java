package com.forA.chatbot.medications.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "medication_schedule_outbox",
    uniqueConstraints = @UniqueConstraint(name = "uk_medication_outbox_schedule",
        columnNames = {"bundle_id", "scheduled_date", "schedule_version"}),
    indexes = {
        @Index(name = "idx_medication_outbox_runnable", columnList = "status,next_attempt_at"),
        @Index(name = "idx_medication_outbox_lease", columnList = "status,lock_until")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationScheduleOutbox extends BaseTimeEntity {

  public enum Status {
    PENDING, PROCESSING, RETRY_WAITING, COMPLETED, SUPERSEDED, EXPIRED, FAILED
  }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bundle_id", nullable = false)
  private Long bundleId;

  @Column(name = "scheduled_date", nullable = false)
  private LocalDate scheduledDate;

  @Column(name = "schedule_version", nullable = false)
  private long scheduleVersion;

  @Column(name = "scheduled_at", nullable = false)
  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "lock_owner", length = 36)
  private String lockOwner;

  @Column(name = "lock_until")
  private Instant lockUntil;

  @Column(name = "last_error", length = 100)
  private String lastError;

  public MedicationScheduleOutbox(MedicationBundle bundle, LocalDate date, Instant now) {
    this.bundleId = bundle.getId();
    this.scheduledDate = date;
    this.scheduleVersion = bundle.getScheduleVersion();
    this.scheduledAt = bundle.scheduledInstantOn(date);
    this.status = !bundle.isScheduledOn(date) ? Status.SUPERSEDED
        : scheduledAt.isAfter(now) ? Status.PENDING : Status.EXPIRED;
    this.nextAttemptAt = status == Status.PENDING ? now : null;
  }

  public String taskId() {
    String identity = bundleId + ":" + scheduledDate + ":" + scheduleVersion;
    return "med-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
  }
}
