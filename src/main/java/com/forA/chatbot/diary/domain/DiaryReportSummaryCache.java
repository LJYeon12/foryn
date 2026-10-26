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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "diary_report_summary_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_diary_report_summary_cache_user_date",
        columnNames = {"user_id", "summary_date"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReportSummaryCache extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "summary_date", nullable = false)
  private LocalDate summaryDate;

  @Column(name = "has_recent_diary_data", nullable = false)
  private boolean hasRecentDiaryData;

  @Column(name = "content", nullable = false, length = 500)
  private String content;

  @Builder
  public DiaryReportSummaryCache(
      User user,
      LocalDate summaryDate,
      boolean hasRecentDiaryData,
      String content) {
    this.user = user;
    this.summaryDate = summaryDate;
    this.hasRecentDiaryData = hasRecentDiaryData;
    this.content = content;
  }

  public boolean hasRecentDiaryData() {
    return hasRecentDiaryData;
  }
}
