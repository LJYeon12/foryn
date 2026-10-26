package com.forA.chatbot.diary.service;

import org.springframework.stereotype.Component;

@Component
public class MonthlySummaryOutputValidator {

  private static final int MIN_LENGTH = 60;
  private static final int MAX_LENGTH = 80;
  private static final int MAX_LINE_COUNT = 2;
  private static final String[] FORBIDDEN_EXPRESSIONS = {
      "ADHD", "우울증", "불안장애", "진단", "처방", "치료", "약을", "복용", "반드시", "무조건", "해야 해요"
  };

  public String normalizeOrFallback(String content) {
    if (content == null || content.isBlank()) {
      return DiaryAiService.FALLBACK_MONTHLY_SUMMARY;
    }

    String normalized = stripWrappingQuotes(content.strip());
    if (isInvalid(normalized)) {
      return DiaryAiService.FALLBACK_MONTHLY_SUMMARY;
    }
    if (!containsForynEmoji(normalized)) {
      normalized += " 🐾";
    }
    return isInvalid(normalized) ? DiaryAiService.FALLBACK_MONTHLY_SUMMARY : normalized;
  }

  private boolean isInvalid(String value) {
    if (value.length() < MIN_LENGTH
        || value.length() > MAX_LENGTH
        || value.lines().count() > MAX_LINE_COUNT
        || looksLikeJsonOrExplanation(value)) {
      return true;
    }
    for (String forbiddenExpression : FORBIDDEN_EXPRESSIONS) {
      if (value.contains(forbiddenExpression)) {
        return true;
      }
    }
    return false;
  }

  private String stripWrappingQuotes(String value) {
    if (value.length() < 2) {
      return value;
    }
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))
        || (value.startsWith("“") && value.endsWith("”"))
        || (value.startsWith("‘") && value.endsWith("’"))) {
      return value.substring(1, value.length() - 1).strip();
    }
    return value;
  }

  private boolean looksLikeJsonOrExplanation(String value) {
    return value.startsWith("{")
        || value.startsWith("[")
        || value.contains("\"forynComment\"")
        || value.contains("제목:")
        || value.contains("설명:")
        || value.contains("해설:");
  }

  private boolean containsForynEmoji(String value) {
    return value.contains("🐾") || value.contains("🐱");
  }
}
