package com.forA.chatbot.chat.domain.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmotionType {
  // 중립 감정
  SO_SO(1, "그저그런(괜찮음)","\uD83D\uDE42", EmotionState.NEUTRAL),
  LISTLESSNESS(2, "무기력함","\uD83D\uDE1E", EmotionState.NEUTRAL),
  LONGING(3, "그리움", "\uD83D\uDE23", EmotionState.NEUTRAL),
  BOREDOM(4, "지루함", "\uD83E\uDD71", EmotionState.NEUTRAL),

  // 긍정 감정
  EXCITED(5, "신남","\uD83D\uDE06", EmotionState.POSITIVE),
  JOY(6, "즐거움","\uD83E\uDD73", EmotionState.POSITIVE),
  PROUD(7, "뿌듯함","\uD83E\uDD17", EmotionState.POSITIVE),
  HAPPY(8, "행복함","\uD83E\uDD70", EmotionState.POSITIVE),
  FLUTTER(9, "설렘","\uD83D\uDE0D", EmotionState.POSITIVE),

  // 부정 감정
  ANGER(10, "화남","\uD83D\uDE21", EmotionState.NEGATIVE),
  ANXIETY(11, "불안함","\uD83D\uDE28", EmotionState.NEGATIVE),
  REGRET(12, "후회됨","\uD83E\uDD7A", EmotionState.NEGATIVE),
  TENSION(13, "긴장됨","\uD83D\uDE16", EmotionState.NEGATIVE),
  SADNESS(14, "슬픔","\uD83D\uDE2D", EmotionState.NEGATIVE),
  ANNOYED(15, "귀찮음","\uD83D\uDE36\u200D\uD83C\uDF2B\uFE0F", EmotionState.NEGATIVE),
  EXHAUSTION(16, "지침","\uD83D\uDE25", EmotionState.NEGATIVE),
  TIRED(17, "피곤함","\uD83D\uDE2A", EmotionState.NEGATIVE);

  private final int id;
  private final String name;
  private final String emoji;
  private final EmotionState state;

  /** 감정 상태 분류: 긍정, 중립, 부정 */
  @Getter
  @RequiredArgsConstructor
  public enum EmotionState {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
  }

  public static EmotionType fromId(int id) {
    return Arrays.stream(values())
        .filter(emotion -> emotion.id == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid EmotionType id: " + id));
  }
}
