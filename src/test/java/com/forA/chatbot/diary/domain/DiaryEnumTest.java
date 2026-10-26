package com.forA.chatbot.diary.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.diary.domain.enums.DiaryQuestion;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import com.forA.chatbot.diary.domain.enums.DiaryTagType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiaryEnumTest {

  @Test
  @DisplayName("diary question ids map to fixed questions")
  void diaryQuestion_fromId() {
    assertThat(DiaryQuestion.fromId(1)).isEqualTo(DiaryQuestion.MOMENT);
    assertThat(DiaryQuestion.fromId(2)).isEqualTo(DiaryQuestion.THOUGHT);
    assertThat(DiaryQuestion.fromId(3)).isEqualTo(DiaryQuestion.EMOTION);
  }

  @Test
  @DisplayName("invalid diary question id throws IllegalArgumentException")
  void diaryQuestion_invalidId() {
    assertThatThrownBy(() -> DiaryQuestion.fromId(4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("diary tag enum contains all fixed tag codes")
  void diaryTagCode_count() {
    assertThat(DiaryTagCode.values()).hasSize(64);
    assertThat(DiaryTagCode.PROUD.getTagType()).isEqualTo(DiaryTagType.POSITIVE);
    assertThat(DiaryTagCode.PROUD.getLabel()).isEqualTo("뿌듯함");
    assertThat(DiaryTagCode.ALONE.getTagType()).isEqualTo(DiaryTagType.LIFE);
    assertThat(DiaryTagCode.ALONE.getLabel()).isEqualTo("혼자 있음");
  }
}
