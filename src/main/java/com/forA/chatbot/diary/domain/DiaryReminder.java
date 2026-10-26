package com.forA.chatbot.diary.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "diary_reminders",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_diary_reminders_user_time",
        columnNames = {"user_id", "reminder_time"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReminder extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "reminder_time", nullable = false)
  private LocalTime reminderTime;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Builder
  public DiaryReminder(User user, LocalTime reminderTime, Boolean enabled) {
    this.user = user;
    this.reminderTime = reminderTime;
    this.enabled = enabled == null || enabled;
  }
}
