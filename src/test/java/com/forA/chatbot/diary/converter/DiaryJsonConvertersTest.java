package com.forA.chatbot.diary.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiaryJsonConvertersTest {

  private final DiaryTagCodeListConverter tagCodeListConverter = new DiaryTagCodeListConverter();
  private final LongListJsonConverter longListJsonConverter = new LongListJsonConverter();

  @Test
  @DisplayName("diary tag converter serializes tag code names")
  void diaryTagConverter_convertToDatabaseColumn() {
    String json = tagCodeListConverter.convertToDatabaseColumn(
        List.of(DiaryTagCode.WORK, DiaryTagCode.PROUD));

    assertThat(json).isEqualTo("[\"WORK\",\"PROUD\"]");
  }

  @Test
  @DisplayName("diary tag converter restores tag codes from json names")
  void diaryTagConverter_convertToEntityAttribute() {
    List<DiaryTagCode> tagCodes =
        tagCodeListConverter.convertToEntityAttribute("[\"WORK\",\"PROUD\"]");

    assertThat(tagCodes).containsExactly(DiaryTagCode.WORK, DiaryTagCode.PROUD);
  }

  @Test
  @DisplayName("diary tag converter returns empty list for invalid json")
  void diaryTagConverter_invalidJson() {
    assertThat(tagCodeListConverter.convertToEntityAttribute("invalid-json")).isEmpty();
  }

  @Test
  @DisplayName("long list converter serializes old reminder ids")
  void longListConverter_convertToDatabaseColumn() {
    assertThat(longListJsonConverter.convertToDatabaseColumn(List.of(1L, 2L, 3L)))
        .isEqualTo("[1,2,3]");
  }

  @Test
  @DisplayName("long list converter restores old reminder ids")
  void longListConverter_convertToEntityAttribute() {
    assertThat(longListJsonConverter.convertToEntityAttribute("[1,2,3]"))
        .containsExactly(1L, 2L, 3L);
  }
}
