package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DiaryAiServiceTest {

  private final DiaryAiService diaryAiService =
      new DiaryAiService(
          null,
          null,
          new MonthlySummaryOutputValidator(),
          new MonthlySummaryRetryExecutor());

  @Test
  @DisplayName("AI 코멘트에 이모지가 없으면 포린이 이모지를 보정한다")
  void normalizeComment_addsForynEmoji() throws Exception {
    String normalized = normalize("오늘의 마음을 차분히 적어준 것만으로도 의미 있어요.");

    assertThat(normalized).endsWith("🐾");
  }

  @Test
  @DisplayName("AI 코멘트가 전체 따옴표로 감싸져 있으면 따옴표만 제거한다")
  void normalizeComment_removesWrappingQuotes() throws Exception {
    String normalized = normalize("\"오늘의 마음을 잘 남겨줬어요 🐱\"");

    assertThat(normalized).isEqualTo("오늘의 마음을 잘 남겨줬어요 🐱");
  }

  @Test
  @DisplayName("AI 코멘트에 진단명이 있으면 fallback을 사용한다")
  void normalizeComment_fallbackForDiagnosticExpression() throws Exception {
    String normalized = normalize("ADHD 때문에 힘들었던 하루였겠어요 🐾");

    assertThat(normalized).isEqualTo(DiaryAiService.FALLBACK_FORYN_COMMENT);
  }

  @Test
  @DisplayName("AI 코멘트가 JSON 형태면 fallback을 사용한다")
  void normalizeComment_fallbackForJsonShape() throws Exception {
    String normalized = normalize("{\"forynComment\":\"오늘의 마음을 잘 남겨줬어요 🐾\"}");

    assertThat(normalized).isEqualTo(DiaryAiService.FALLBACK_FORYN_COMMENT);
  }

  @Test
  @DisplayName("AI metric feature 이름은 문서 계약과 일치한다")
  void featureName_matchesDesignDocument() {
    assertThat(ReflectionTestUtils.getField(DiaryAiService.class, "FEATURE_NAME"))
        .isEqualTo("diary_foryn_comment");
  }

  @Test
  @DisplayName("최근 이틀 일기가 없으면 닉네임을 사용한 안내 문구를 반환한다")
  void noRecentDiarySummary_usesNicknameAndRecentTwoDaysCopy() {
    String summary = DiaryAiService.noRecentDiarySummary("테스트닉네임");

    assertThat(summary)
        .startsWith("최근 이틀간 기록이 비어있어 테스트닉네임님의")
        .doesNotContain("최근의", "유저님");
  }

  @Test
  @DisplayName("최근 이틀 AI 요약 프롬프트는 닉네임 변수와 고정 기간 문구를 사용한다")
  void monthlySummaryPrompt_usesNicknameVariableAndRecentTwoDaysCopy() {
    String prompt = (String) ReflectionTestUtils.getField(
        DiaryAiService.class, "MONTHLY_SUMMARY_PROMPT");

    assertThat(prompt)
        .contains("최근 이틀간", "{nickname}님")
        .doesNotContain("최근의", "유저님");
  }

  @Test
  @DisplayName("최근 이틀 AI 요약 결과의 기간과 사용자 호칭을 보정한다")
  void applyMonthlySummaryCopy_replacesPeriodAndGenericUserName() throws Exception {
    String summary = applyMonthlySummaryCopy(
        "최근의 기록을 보면 유저님의 마음이 지쳐 있었어요 🐾", "테스트닉네임");

    assertThat(summary)
        .isEqualTo("최근 이틀간 기록을 보면 테스트닉네임님의 마음이 지쳐 있었어요 🐾");
  }

  private String normalize(String value) throws Exception {
    Method method = DiaryAiService.class.getDeclaredMethod("normalizeComment", String.class);
    method.setAccessible(true);
    return (String) method.invoke(diaryAiService, value);
  }

  private String applyMonthlySummaryCopy(String value, String nickname) throws Exception {
    Method method = DiaryAiService.class.getDeclaredMethod(
        "applyMonthlySummaryCopy", String.class, String.class);
    method.setAccessible(true);
    return (String) method.invoke(diaryAiService, value, nickname);
  }
}
