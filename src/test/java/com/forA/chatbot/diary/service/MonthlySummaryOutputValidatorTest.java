package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlySummaryOutputValidatorTest {

  private final MonthlySummaryOutputValidator validator = new MonthlySummaryOutputValidator();

  @Test
  @DisplayName("월간 요약이 80자를 넘으면 fallback을 반환한다")
  void normalizeOrFallback_returnsFallbackWhenSummaryExceedsMaximumLength() {
    String tooLong = "최근 며칠 동안 해야 할 일이 쌓이면서 마음이 많이 지쳤겠어요. 지금은 잠시 쉬어가도 괜찮고, "
        + "당신의 가치는 성과로 정해지지 않으니 스스로를 너무 몰아붙이지 말아요 🐾";

    assertThat(validator.normalizeOrFallback(tooLong))
        .isEqualTo(DiaryAiService.FALLBACK_MONTHLY_SUMMARY);
  }

  @Test
  @DisplayName("월간 요약이 세 줄이면 fallback을 반환한다")
  void normalizeOrFallback_returnsFallbackWhenSummaryHasMoreThanTwoLines() {
    String threeLines = "오늘 마음이 무거웠겠어요.\n잠시 쉬어가도 괜찮아요.\n포린이가 함께할게요 🐾";

    assertThat(validator.normalizeOrFallback(threeLines))
        .isEqualTo(DiaryAiService.FALLBACK_MONTHLY_SUMMARY);
  }
}
