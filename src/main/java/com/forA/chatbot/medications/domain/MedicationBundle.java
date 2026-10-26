package com.forA.chatbot.medications.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "medication_bundles")
public class MedicationBundle extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "bundle_name", nullable = false, length = 50)
  private String bundleName;

  @Column(name = "day_of_week")
  private String dayOfWeek;

  @Column(name = "scheduled_time")
  private LocalTime scheduledTime;

  @Version
  @Column(name = "schedule_version", nullable = false)
  private long scheduleVersion;

  @Column(name = "is_deleted")
  private Boolean isDeleted = false;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  public MedicationBundle(
      User user,
      String bundleName,
      String dayOfWeek,
      LocalTime scheduledTime) {
    this.user = user;
    this.bundleName = bundleName;
    this.dayOfWeek = dayOfWeek;
    this.scheduledTime = scheduledTime;
    this.isDeleted = false;
  }

  // 복용 계획 업데이트 메서드
  public void updateMedicationPlan(
      String bundleName,
      String dayOfWeek,
      LocalTime scheduledTime) {
    this.bundleName = bundleName;
    this.dayOfWeek = dayOfWeek;
    this.scheduledTime = scheduledTime;
  }

  // 삭제 로직 호출 시 필드 업데이트
  public void delete() {
    this.isDeleted = true;
    this.deletedAt = LocalDateTime.now();
  }

  public Instant scheduledInstantOn(LocalDate date) {
    return date.atTime(scheduledTime.plusHours(9)).atZone(ZoneId.of("Asia/Seoul")).toInstant();
  }

  public boolean isScheduledOn(LocalDate date) {
    return !Boolean.TRUE.equals(isDeleted) && dayOfWeek != null
        && dayOfWeek.contains(date.getDayOfWeek().name());
  }
}
