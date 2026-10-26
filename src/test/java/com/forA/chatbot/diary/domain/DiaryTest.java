package com.forA.chatbot.diary.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiaryTest {

  @Test
  @DisplayName("DRAFT 답변과 AI 코멘트를 같은 row에 덮어쓴다")
  void overwriteDraft_updatesOnlyEditableFields() {
    Diary diary = Diary.builder()
        .diaryDate(LocalDate.of(2026, 6, 20))
        .status(DiaryStatus.DRAFT)
        .momentAnswer("기존 순간")
        .thoughtAnswer("기존 생각")
        .emotionAnswer("기존 감정")
        .forynComment("기존 코멘트")
        .build();

    diary.overwriteDraft("새 순간", "새 생각", "새 감정", "새 코멘트");

    assertThat(diary.getDiaryDate()).isEqualTo(LocalDate.of(2026, 6, 20));
    assertThat(diary.getStatus()).isEqualTo(DiaryStatus.DRAFT);
    assertThat(diary.getMomentAnswer()).isEqualTo("새 순간");
    assertThat(diary.getThoughtAnswer()).isEqualTo("새 생각");
    assertThat(diary.getEmotionAnswer()).isEqualTo("새 감정");
    assertThat(diary.getForynComment()).isEqualTo("새 코멘트");
  }

  @Test
  @DisplayName("COMPLETED 기록은 Draft 덮어쓰기를 거부한다")
  void overwriteDraft_rejectsCompleted() {
    Diary diary = Diary.builder().status(DiaryStatus.COMPLETED).build();

    assertThatThrownBy(() ->
        diary.overwriteDraft("새 순간", "새 생각", "새 감정", "새 코멘트"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only DRAFT diary can be overwritten");
  }
}
