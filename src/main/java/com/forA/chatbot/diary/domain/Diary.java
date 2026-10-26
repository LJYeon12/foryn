package com.forA.chatbot.diary.domain;

import com.forA.chatbot.diary.converter.DiaryTagCodeListConverter;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
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
import jakarta.persistence.Index;
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
@Table(
    name = "diaries",
    indexes = {
        @Index(name = "idx_diaries_user_date", columnList = "user_id, diary_date"),
        @Index(name = "idx_diaries_user_date_status", columnList = "user_id, diary_date, status"),
        @Index(name = "idx_diaries_user_created_at", columnList = "user_id, created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "diary_date", nullable = false)
  private LocalDate diaryDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DiaryStatus status;

  @Column(name = "moment_answer", nullable = false, length = 500)
  private String momentAnswer;

  @Column(name = "thought_answer", nullable = false, length = 500)
  private String thoughtAnswer;

  @Column(name = "emotion_answer", nullable = false, length = 500)
  private String emotionAnswer;

  @Column(name = "foryn_comment", length = 1000)
  private String forynComment;

  @Column(name = "emotion_score")
  private Integer emotionScore;

  @Convert(converter = DiaryTagCodeListConverter.class)
  @Column(name = "tag_codes", columnDefinition = "JSON")
  private List<DiaryTagCode> tagCodes = new ArrayList<>();

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Builder
  public Diary(
      User user,
      LocalDate diaryDate,
      DiaryStatus status,
      String momentAnswer,
      String thoughtAnswer,
      String emotionAnswer,
      String forynComment,
      Integer emotionScore,
      List<DiaryTagCode> tagCodes,
      LocalDateTime completedAt) {
    this.user = user;
    this.diaryDate = diaryDate;
    this.status = status;
    this.momentAnswer = momentAnswer;
    this.thoughtAnswer = thoughtAnswer;
    this.emotionAnswer = emotionAnswer;
    this.forynComment = forynComment;
    this.emotionScore = emotionScore;
    this.tagCodes = tagCodes == null ? new ArrayList<>() : new ArrayList<>(tagCodes);
    this.completedAt = completedAt;
  }

  public void complete(Integer emotionScore, List<DiaryTagCode> tagCodes, LocalDateTime completedAt) {
    // 이 초안을 지우지 않고 완료된 일기로 바꾼다. 그래서 diaryId는 그대로다.
    this.emotionScore = emotionScore;
    this.tagCodes = tagCodes == null ? new ArrayList<>() : new ArrayList<>(tagCodes);
    this.completedAt = completedAt;
    this.status = DiaryStatus.COMPLETED;
  }

  public void overwriteDraft(
      String momentAnswer,
      String thoughtAnswer,
      String emotionAnswer,
      String forynComment) {
    // 과거 날짜 일기는 새로 만들지 않고, 기존 초안의 내용만 바꾼다.
    if (status != DiaryStatus.DRAFT) {
      throw new IllegalStateException("Only DRAFT diary can be overwritten");
    }
    this.momentAnswer = momentAnswer;
    this.thoughtAnswer = thoughtAnswer;
    this.emotionAnswer = emotionAnswer;
    this.forynComment = forynComment;
  }
}
